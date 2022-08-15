package sttp.tapir.serverless.aws.cdk

object TreeBuilder {
  /* TypeScript CDK library requires to declare endpoints in an cascade approach like below.
     This object responsibility is to build unequivocal tree based on all provided urls

     //hello
     const hello = api.root.addResource('hello');
     hello.addMethod('GET');

     //hello/{id}
     const helloId = hello.addResource('{id}')
     helloId.addMethod('GET');
   */
  def run(urls: List[Url]): Nodes = {
    urls.foldLeft(List.empty[Node]) { (acc, url) =>
      TreeBuilder.add(acc, url.path.dropRight(1), url.path.last, url.method) // fixme what if empty?
    }
  }

  //fixme just provide Url object (which contains all required data)
  private def add(current: Nodes, path: List[Segment], segment: Segment, method: String): Nodes = {
    if (path.isEmpty) {
      if (current.isEmpty) {
        return current ++ List(Node(segment, List(method)))
      } else {
        val seg = find(current, segment)
        return current.filter(i => i.name != segment) :+ seg.copy(methods = (seg.methods :+ method).sorted)
      }
    }

    val s: Node = find(current, path.head)
    val remaining = current.filter(x => x.name != path.head)

    sort(remaining :+ Node(s.name, s.methods, add(s.content, path.tail, segment, method)))
  }

  private def find(current: Nodes, name: Segment): Node = { // fixme private
    val value = current.filter(i => i.name.toString == name.toString)
    if (value.isEmpty) Node(name)
    else value.head
  }

  // fixme find better way to compare Segments
  private def sort(x: Nodes): Nodes = x.sortWith((x, y) => x.name.toString < y.name.toString)
}
