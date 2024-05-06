# Google Java Style Formatter

This project aims to ensure adherence to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for maintaining consistent and readable Java code throughout the project. Consistent coding conventions not only enhance code readability but also streamline collaboration among developers by establishing a shared set of coding standards.

Included within this project is an optional script titled `google-java-format-diff.py`, which leverages the official [google-java-format](https://github.com/google/google-java-format/tree/master) implementation to facilitate code formatting checks.

## How to Utilize the Script

Before utilizing the script, it is imperative to install the `google-java-format` binary on your local machine. This can be accomplished either by downloading it from the [official releases](https://github.com/google/google-java-format/releases) or by employing a package manager like Homebrew:

```bash
$ brew install google-java-format
```

The script operates by parsing input from a unified diff and subsequently reformatting all modified lines. This feature proves invaluable for the comprehensive reformatting of staged lines. For instance, to preview all reformatted modified lines within a staged diff, execute the following command:

```bash
$ git diff -U0 --staged | ./fmt/google-java-format-diff.py -p1 --skip-sorting-imports --skip-reflowing-long-strings
```

The output will display a diff post-formatting:

```bash
--- src/main/java/co/rsk/federate/log/FederateLogger.java       (before formatting)
+++ src/main/java/co/rsk/federate/log/FederateLogger.java       (after formatting)
@@ -25,7 +25,12 @@
     private Block currentRskBestBlock;
     private StoredBlock currentBtcBestBlock;

-    public FederateLogger( FederatorSupport federatorSupport, CurrentTimeProvider currentTimeProvider, LoggerProvider loggerProvider, long minTimeBetweenLogs, long minBlocksBetweenLogs) {
+  public FederateLogger(
+      FederatorSupport federatorSupport,
+      CurrentTimeProvider currentTimeProvider,
+      LoggerProvider loggerProvider,
+      long minTimeBetweenLogs,
+      long minBlocksBetweenLogs) {
         this.federatorSupport = federatorSupport;
         this.currentTimeProvider = currentTimeProvider;
         this.minTimeBetweenLogs = minTimeBetweenLogs;
```

To implement the formatting changes, include the `-i` flag. For further details, refer to `./fmt/google-java-format --help`.
