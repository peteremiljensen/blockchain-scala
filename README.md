# freechain-scala
A modular blockchain framework for Scala, making it easy to create blockchain applications with maximum flexibility.

The Scala implementation is developed using Akka for threading and WebSocket connections. Moreover, SBT is used to build or package the project into a JAR-file for use in another project.

# Usage Example
Special care is been taken to assure its easy use without compromising applicability and throughput. The uncomplicated use of the framework is demonstrated by the following simple Scala program, which starts a minimal blockchain node listening on port 9000&mdash;ready to receive loaves and blocks.

```scala
import dk.diku.freechain._

implicit val validator = Validator(_ => true, _ => true, (a, _) => a)
val node: Node = new Node(9000)
```

More elaborate examples can be found in the following repository on github:
[freechain-scala-example](https://www.github.com/peteremiljensen/freechain-scala-example)
