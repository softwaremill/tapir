package sttp.tapir.serverless.aws.cdk.core

private[core] case class Node(
    name: Segment,
    methods: List[Method] = List.empty[Method],
    content: List[Node] = List.empty[Node]
)

private[core] object Tree {
  /* TypeScript CDK library requires to declare endpoints in an cascade approach like below.
     This object responsibility is to build unequivocal tree based on all provided urls

     //hello
     const hello = api.root.addResource('hello');
     hello.addMethod('GET');

     //hello/{id}
     const helloId = hello.addResource('{id}')
     helloId.addMethod('GET');
   */
  def build(urls: List[Request]): Tree = {
    urls.foldLeft(List.empty[Node]) { (acc, url) => //rename
      add(acc, url.path, url.method) // fixme what if empty?
    }
  }

  private def add(current: Tree, fullPath: List[Segment], method: Method): Tree = {

    val pathPrefix = fullPath.dropRight(1)
    val lastSegment = fullPath.last

    if (pathPrefix.isEmpty) {
      if (current.isEmpty) {
        return current ++ List(Node(lastSegment, List(method)))
      } else {
        val seg = fetch(current, lastSegment)
        return current.filter(i => i.name != lastSegment) :+ seg.copy(methods = (seg.methods :+ method).sorted)
      }
    }

    val s: Node = fetch(current, pathPrefix.head)
    val remaining = current.filter(x => x.name != pathPrefix.head)

    sort(remaining :+ Node(s.name, s.methods, add(s.content, pathPrefix.tail :+ lastSegment, method)))
  }

  private def fetch(current: Tree, name: Segment): Node = {
    val value = current.filter(i => i.name.toString == name.toString)
    if (value.isEmpty) Node(name)
    else value.head
  }

  private def sort(tree: Tree): Tree = tree.sortWith((x, y) => x.name.toString < y.name.toString)
}
