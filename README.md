# JDBC CLI

A command line interface for executing simple database operations (currently just queries) via JDBC.

Requires Java 17+ to run.

## Examples
With inline SQL, write CSV output to standard output:
```console
 java -jar jdbc-cli.jar jdbc.props --query "select 2+2 as four, 'hello, friend' as greeting"
 ```

Output:
 ```
four,greeting
4,"hello, friend"
```

With output file specified and output type (csv) determined implicitly:
```console
java -jar jdbc-cli.jar jdbc.props --query "select * from drug" --output-file my-output.csv
```

With both output file and output type specified:
```console
java -jar jdbc-cli.jar jdbc.props --query "select * from drug" --output-file my-output --output-type tsv
```
With SQL from file and tsv output to standard output with no header row:
```console
echo "select * from drug where id > 2" >> my-query.sql
java -jar jdbc-cli.jar jdbc.props --query-file my-query.sql --output-type tsv --header false
```

## Parameters and options

```console
java -jar jdbc-cli.jar [options] <jdbc-props-file>

  jdbc-props-file: JDBC properties file, with properties:
      jdbc.driverClassName
      jdbc.url
      jdbc.username
      jdbc.password
  [options]:
    --query-file
      File containing the SQL query to be run. Exactly one of --query
      and --query-file should be specified.
    --query
      Text of SQL query to be run. Exactly one of --query and --query-file
      should be specified.
    --output-file
      The output file to which results are written. Optional, if absent then
      output will be written to standard output.
    --fetch-size <number>
      Fetch size passed to the underlying jdbc implementation for the query.
      Optional, default is 100.
    --output-type <tsv|csv>
      Type of delimited file output: 'csv' for comma separated values, or
      'tsv' for tab-separated values. Optional, defaults to extension of
      output-file option if provided and recognized, else 'csv'.
    --header <true|false>
      Whether to write a columns header row to output. Optional with
      default of true.
```

## Build

Build a runnable jar (via `java -jar`) but without any JDBC driver included.

```console
mvn clean package
```

Use profile `ora` and/or `pg` to include Oracle and/or Postgres jdbc drivers in the
jar. For example, to build a runnable jar with both PostgreSQL and Oracle drivers
included:

```console
mvn clean package -Pora,pg
```
