package transformation;

import model.*;
import org.javatuples.Triplet;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TransformationP2 implements Transformation {
    @Override
    public boolean isConditionCompleted(ModelGraph graph, InteriorNode interiorNode) {
        Triplet<Vertex, Vertex, Vertex> triangle;
        try {
            triangle = getOrderedTriangle(interiorNode.getTriangleVertexes(), graph);
        } catch (Exception e) {
            return false;
        }
        return areAllVertexType(triangle) &&
                isTransformationConditionFulfilled(graph, triangle);
    }

    @Override
    public ModelGraph transformGraph(ModelGraph graph, InteriorNode interiorNode) {
        Triplet<Vertex, Vertex, Vertex> triangle = getOrderedTriangle(interiorNode.getTriangleVertexes(), graph); //triangle of vertexes

        Vertex first = triangle.getValue0();
        Vertex second = triangle.getValue1();
        Vertex third = triangle.getValue2();
        Vertex hanging = getHangingVertexBetween(first, second, graph); //hanging vertex

        // updated transformation -> convert hanging node to simple one
        hanging.setVertexType(VertexType.SIMPLE_NODE);

        GraphEdge firstToHanging = graph.getEdgeById(first.getEdgeBetween(hanging).getId()).orElseThrow(()->new RuntimeException("Unknown edge id"));
        GraphEdge hangingToSecond = graph.getEdgeById(hanging.getEdgeBetween(second).getId()).orElseThrow(()->new RuntimeException("Unknown edge id"));

        //remove old
        graph.removeInterior(interiorNode.getId());
        graph.deleteEdge(first, hanging);
        graph.deleteEdge(hanging, second);

        //create new vertex
        Vertex inserted = hanging.setVertexType(VertexType.SIMPLE_NODE);

        //connect it to other
        String edgeFromOneToVertexId = first.getId().concat(inserted.getId());
        GraphEdge fromOneToVertex = graph.insertEdge(edgeFromOneToVertexId, first, inserted);
        fromOneToVertex.setB(firstToHanging.getB());

        String edgeFromVertexToTwoId = inserted.getId().concat(second.getId());
        GraphEdge fromVertexToTwo = graph.insertEdge(edgeFromVertexToTwoId, inserted, second);
        fromVertexToTwo.setB(hangingToSecond.getB());

        String edgeFromVertexToThird = inserted.getId().concat(third.getId());
        GraphEdge fromVertexToThird = graph.insertEdge(edgeFromVertexToThird, inserted, third);
        fromVertexToThird.setB(false);

        //Rebuild interior
        String leftInteriorId = first.getId().concat(inserted.getId()).concat(third.getId());
        InteriorNode left = graph.insertInterior(leftInteriorId, first, inserted, third);
        left.setPartitionRequired(false);

        String rightInteriorId = inserted.getId().concat(second.getId()).concat(third.getId());
        InteriorNode right = graph.insertInterior(rightInteriorId, inserted, second, third);
        right.setPartitionRequired(false);
        return graph;
    }

    private Optional<Vertex> getHangingVertexBetweenOp(Vertex v1, Vertex v2, ModelGraph graph){
        List<Vertex> available = graph.getVertexesBetween(v1, v2);

        return available
                .stream()
                .filter(vertex -> vertex.getCoordinates().equals(Point3d.middlePoint(v1.getCoordinates(), v2.getCoordinates())))
                .filter(vertex -> vertex.getVertexType() == VertexType.HANGING_NODE)
                .findAny();
    }

    private Vertex getHangingVertexBetween(Vertex v1, Vertex v2, ModelGraph graph){
        return getHangingVertexBetweenOp(v1, v2, graph)
                .orElseThrow(() -> new RuntimeException("Hanging vertex between " + v1.getId() +" and " + v2.getId() + " not found"));
    }

    private boolean isTransformationConditionFulfilled(ModelGraph graph, Triplet<Vertex, Vertex, Vertex> triangle){
        Vertex first = triangle.getValue0();
        Vertex second = triangle.getValue1();
        Vertex third = triangle.getValue2();
        Vertex hanging = getHangingVertexBetween(first, second, graph);

        GraphEdge L1 = graph.getEdgeBetweenNodes(first, hanging).orElse(null);
        GraphEdge L2 = graph.getEdgeBetweenNodes(hanging, second).orElse(null);
        GraphEdge L3 = graph.getEdgeBetweenNodes(second, third).orElse(null);
        GraphEdge L4 = graph.getEdgeBetweenNodes(third, first).orElse(null);
        if (L1 == null || L2 == null || L3 == null || L4 == null) {
            return false;
        }

        return (L1.getL() + L2.getL()) >= L3.getL() && (L1.getL() + L2.getL()) >= L4.getL();
    }

    private Triplet<Vertex, Vertex, Vertex> getOrderedTriangle(Triplet<Vertex, Vertex, Vertex> v, ModelGraph graph){
        if(getHangingVertexBetweenOp(v.getValue0(), v.getValue1(), graph).isPresent()){
            return v;
        } else if (getHangingVertexBetweenOp(v.getValue0(), v.getValue2(), graph).isPresent()){
            return new Triplet<>(v.getValue2(), v.getValue0(), v.getValue1());
        } else if(getHangingVertexBetweenOp(v.getValue1(), v.getValue2(), graph).isPresent()) {
            return new Triplet<>(v.getValue1(), v.getValue2(), v.getValue0());
        }
        throw new RuntimeException("Configuration with hanging vertex between 2 vertexes was not found");
    }

    private boolean areAllVertexType(Triplet<Vertex, Vertex, Vertex> triangle) {
        return triangle.toList().stream().map(e -> {
            Vertex v = (Vertex)e;
            return isVertexType(v);
        }).reduce(true, (acc, e) -> acc && e);
    }

    private boolean isVertexType(Vertex v){
        return v.getVertexType() == VertexType.SIMPLE_NODE;
    }
}
