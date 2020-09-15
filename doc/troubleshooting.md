# Troubleshooting

## StackOverflowException during compilation

Sidenote for scala 2.12.4 and higher: if you encounter an issue with compiling your project because of 
a `StackOverflowException` related to [this](https://github.com/scala/bug/issues/10604) scala bug, 
please increase your stack memory. Example:

```
sbt -J-Xss4M clean compile
```

## Logging of generated macros code 
For some cases, it may be helpful to examine how generated macros code looks like.
To do that, just set an environmental variable and check compilation logs for details.    

```
export TAPIR_LOG_GENERATED_CODE=true
```
