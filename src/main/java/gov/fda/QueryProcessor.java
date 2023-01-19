package gov.fda;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jdbi.v3.core.Jdbi;
import com.univocity.parsers.common.*;
import com.univocity.parsers.csv.*;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;

import static gov.fda.Args.*;

public record QueryProcessor
  (
    int fetchSize,
    OutputType outputType,
    boolean includeColumnsHeader
  )
{
  final static String usage =
    "Expected arguments: [options] <jdbc-props-file>\n" +
    "  jdbc-props-file: JDBC properties file, with properties jdbc.driverClassName, jdbc.url, " +
    "jdbc.username, jdbc.password.\n" +
    "  [options]:\n" +
    "    --query-file: File containing the SQL query to be run. Exactly one of --query and --query-file should be specified.\n" +
    "    --query: Text of SQL query to be run. Exactly one of --query and --query-file should be specified.\n" +
    "    --output-file: File containing the SQL query to be run. Optional, if absent then output will be written to standard output.\n" +
    "    --fetch-size <number>: Fetch size specified for the underlying jdbc implementation for the query. Optional, default is 100.\n" +
    "    --output-type <tsv|csv>: Type of delimited file output, 'csv' for comma separated values, or 'tsv' for tab-separated values.\n" +
    "        Optional, defaults to extension of output-file option if provided and recognized, else 'csv'.\n" +
    "    --header <true|false>: Whether to write a columns header row to output. Optional, default is true.\n";

  enum OutputType { CSV, TSV }

  public static void main(String[] args)
  {
    if ( args.length == 1 && (args[0].equals("-h") || args[0].equals("--help")) )
    {
      System.out.println(usage);
      return;
    }

    List<String> remArgs = new ArrayList<>(Arrays.asList(args));

    @Nullable Path sqlFile = pluckStringOption(remArgs, "--query-file").map(Paths::get).orElse(null);
    @Nullable String sqlText = pluckStringOption(remArgs, "--query").orElse(null);
    @Nullable Path outputFile = pluckStringOption(remArgs, "--output-file").map(Paths::get).orElse(null);
    int fetchSize = pluckIntOption(remArgs, "--fetch-size", 100);
    OutputType outputType = pluckStringOption(remArgs, "--output-type").map(String::toUpperCase).map(OutputType::valueOf)
                            .orElse(getOutputTypeFor(outputFile).orElse(OutputType.CSV));
    boolean includeColsHeader = pluckStringOption(remArgs, "--header").map(Boolean::parseBoolean).orElse(true);

    if ( remArgs.size() != 1 )
    {
      System.err.println(usage);
      System.exit(1);
    }

    Path jdbcPropsFile = Paths.get(args[0]);

    if ( !Files.isRegularFile(jdbcPropsFile) )
      throw new RuntimeException("Connection properties file was not found: " + jdbcPropsFile);
    if ( sqlFile == null && sqlText == null || sqlFile != null && sqlText != null )
      throw new RuntimeException("Exactly one of --query and --query-file can be specified.");
    if ( sqlFile != null && !Files.isRegularFile(sqlFile) )
      throw new RuntimeException("SQL file was not found: " + sqlFile);

    try
    {
      var queryProcessor = new QueryProcessor(fetchSize, outputType, includeColsHeader);

      String sql = sqlText != null ? sqlText : Files.readString(nn(sqlFile, "query file"));

      Jdbi jdbi = createJdbi(jdbcPropsFile);

      queryProcessor.processQuery(jdbi, sql, outputFile);

      System.exit(0);
    }
    catch(Throwable t)
    {
      System.err.println(t.getMessage());
      System.exit(1);
    }
  }

  private void processQuery
    (
      Jdbi jdbi,
      String sql,
      @Nullable Path outputFile
    )
  {
    try (var outputWriter = outputFile != null
           ? Files.newBufferedWriter(outputFile)
           : new BufferedWriter(new OutputStreamWriter(System.out)))
    {
      AbstractWriter<?> xsvWriter = this.outputType == OutputType.CSV
        ? new CsvWriter(outputWriter, new CsvWriterSettings())
        : new TsvWriter(outputWriter, new TsvWriterSettings());

      record ResultsWritingState(int columnCount) {}

      jdbi.useHandle(db -> {
        db.createQuery(sql)
        .setFetchSize(this.fetchSize)
        .reduceResultSet(new ResultsWritingState(-1), (state, rs, ctx) -> {
          if (state.columnCount == -1)
          {
            ResultSetMetaData rsmd = rs.getMetaData();
            if (this.includeColumnsHeader)
              writeColumnsHeader(rsmd, xsvWriter);
            xsvWriter.writeRow(makeRowStrings(rs, rsmd.getColumnCount()));
            return new ResultsWritingState(rsmd.getColumnCount());
          }
          xsvWriter.writeRow(makeRowStrings(rs, state.columnCount));
          return state;
        });
      });

      xsvWriter.close();
    }
    catch(Exception e) { throw new RuntimeException(e); }
  }

  private void writeColumnsHeader
    (
      ResultSetMetaData rsmd,
      AbstractWriter<?> xsvWriter
    )
    throws SQLException
  {
    String[] colNames = new String[rsmd.getColumnCount()];

    for (int cix = 0; cix < colNames.length; ++cix)
    {
      String label = rsmd.getColumnLabel(cix+1);
      if (label != null)
        colNames[cix] = label;
      else
      {
        String name = rsmd.getColumnName(cix+1);
        colNames[cix] = name != null ? name : "";
      }
    }

    xsvWriter.writeHeaders(colNames);
  }

  private String[] makeRowStrings(ResultSet rs, int columnCount) throws SQLException
  {
    String[] res = new String[columnCount];

    for (int cix=0; cix < columnCount; ++cix)
    {
      var s = rs.getString(cix+1);
      res[cix] = s != null ? s : "";
    }

    return res;
  }

  private static Jdbi createJdbi(Path propsFile)
  {
    try
    {
      Properties props = new Properties();
      props.load(Files.newInputStream(propsFile));

      if (!props.containsKey("jdbc.driverClassName") ||
          !props.containsKey("jdbc.url") ||
          !props.containsKey("jdbc.username") ||
          !props.containsKey("jdbc.password"))
        throw new RuntimeException(
          "Expected connection properties " +
          "{ jdbc.driverClassName, jdbc.url, jdbc.username, jdbc.password } " +
          "in connection properties file."
        );

      Class.forName(nn(props.getProperty("jdbc.driverClassName"), "jdbc.DriverClassName property"));

      Jdbi jdbi =
        Jdbi.create(
          nn(props.getProperty("jdbc.url"), "jdbc.url property"),
          nn(props.getProperty("jdbc.username"), "jdbc.username property"),
          nn(props.getProperty("jdbc.password"), "jdbc.password property")
        );

      return jdbi;
    }
    catch(Exception e) { throw new RuntimeException(e); }
  }

  private static Optional<OutputType> getOutputTypeFor(@Nullable Path outputFile)
  {
    if (outputFile != null)
    {
      String lcName = nn(outputFile.getFileName(), "output file name").toString().toLowerCase();
      if (lcName.endsWith(".csv"))
        return Optional.of(OutputType.CSV);
      else if (lcName.endsWith(".tsv"))
        return Optional.of(OutputType.TSV);
    }

    return Optional.empty();
  }

  private static <T> T nn(@Nullable T t, String what)
  {
    if (t == null)
      throw new RuntimeException(what + " is required.");
    return t;
  }
}
