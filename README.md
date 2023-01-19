# JDBC CLI

A command line interface for executing simple database operations (currently just queries) via JDBC.

## Run

```console
java -jar <jdbc-cli-xxx.jar> [options] <jdbc-props-file>

  jdbc-props-file: JDBC properties file, with properties jdbc.driverClassName, jdbc.url, jdbc.username, jdbc.password.
  [options]:
    --query-file: File containing the SQL query to be run. Exactly one of --query and --query-file should be specified.
    --query: Text of SQL query to be run. Exactly one of --query and --query-file should be specified.
    --output-file: File containing the SQL query to be run. Optional, if absent then output will be written to standard output.
    --fetch-size <number>: Fetch size specified for the underlying jdbc implementation for the query. Optional, default is 100.
    --output-type <tsv|csv>: Type of delimited file output, 'csv' for comma separated values, or 'tsv' for tab-separated values.
        Optional, defaults to extension of output-file option if provided and recognized, else 'csv'.
    --header <true|false>: Whether to write a columns header row to output. Optional, default is true.
```

### Examples
Example - With inline SQL, write CSV output to standard output:
```console
 java -jar jdbc-cli.jar jdbc.props --query "select 2+2 as four, 'hello, friend' as greeting"
 ```

Example - With output file specified and output type (csv) determined implicitly:
```console
java -jar jdbc-cli.jar jdbc.props --query "select * from drug" --output-file my-output.csv
```

Example - With both output file and output type specified:
```console
java -jar jdbc-cli.jar jdbc.props --query "select * from drug" --output-file my-output --output-type tsv
```
Example - With SQL from file and tsv output to standard output with no header row:
```console
echo "select * from drug where id > 2" >> my-query.sql
java -jar jdbc-cli.jar jdbc.props --query-file my-query.sql --output-type tsv --header false
```

## Build

```
mvn clean package
```
