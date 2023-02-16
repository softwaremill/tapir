package sttp.tapir.serverless.aws.cdk.internal

private[internal] case class Node(
    name: Segment,
    methods: List[Method] = List.empty[Method],
    children: List[Node] = List.empty[Node]
)

private[internal] object Tree {
  /* TypeScript CDK library requires to declare endpoints in an cascade approach like below.
     This object responsibility is to build unequivocal tree based on all provided urls

     //hello
     const hello = api.root.addResource('hello');
     hello.addMethod('GET');

     //hello/{id}
     const helloId = hello.addResource('{id}')
     helloId.addMethod('GET');
   */
  def fromRequests(urls: List[Request]): Tree = {
    urls.foldLeft(List.empty[Node]) { (acc, request) =>
      add(acc, request.path, request.method)
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

    sort(remaining :+ Node(s.name, s.methods, add(s.children, pathPrefix.tail :+ lastSegment, method)))
  }

  private def fetch(current: Tree, name: Segment): Node = {
    val value = current.filter(n => n.name == name)
    if (value.isEmpty) Node(name)
    else value.head
  }

  private def sort(tree: Tree): Tree = tree.sortWith((x, y) => x.name.toString < y.name.toString)
}
