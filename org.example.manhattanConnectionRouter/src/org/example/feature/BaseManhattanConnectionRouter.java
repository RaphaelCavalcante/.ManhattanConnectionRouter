/*******************************************************************************
 * Copyright (c) offset11, offset12 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-voffset.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/
package org.example.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.bpmn2.modeler.core.di.DIUtils;
import org.eclipse.bpmn2.modeler.core.features.BendpointConnectionRouter;
import org.eclipse.bpmn2.modeler.core.features.ConnectionRoute;
import org.eclipse.bpmn2.modeler.core.features.DetourPoints;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil.AnchorLocation;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil.BoundaryAnchor;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil.LineSegment;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.FixPointAnchor;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;


/**
 * A Connection Router that constrains all line segments of a connection to be either
 * horizontal or vertical; thus, diagonal lines are split into two segments that are
 * horizontal and vertical.
 * 
 * This is a final class because it needs to ensure the routing info for
 * the connection is cleaned up when it's done, so we don't want to allow
 * this class to be subclassed.
 */
public class BaseManhattanConnectionRouter extends BendpointConnectionRouter {

	protected LineSegment sourceTopEdge;
	protected LineSegment sourceBottomEdge;
	protected LineSegment sourceLeftEdge;
	protected LineSegment sourceRightEdge;

	protected LineSegment targetTopEdge;
	protected LineSegment targetBottomEdge;
	protected LineSegment targetLeftEdge;
	protected LineSegment targetRightEdge;

	static final int offset = 10;
	static boolean testRouteSolver = false;
	private Point end;

	enum Orientation {
		HORIZONTAL, VERTICAL, NONE
	};

	public BaseManhattanConnectionRouter(IFeatureProvider fp, AnchorVerifier anchorVerifier) {
		super(fp);
		this.anchorVerifier = anchorVerifier;
	}

	private AnchorVerifier anchorVerifier;

	@Override
	protected ConnectionRoute calculateRoute() {

		if (isSelfConnection())
			return super.calculateRoute();



		// The list of all possible routes. The shortest will be used.
		List<ConnectionRoute> allRoutes = new ArrayList<ConnectionRoute>();

		List <BoundaryAnchor> sourceBoundaryAnchors = getBoundaryAnchors(source);
		List <BoundaryAnchor>  targetBoundaryAnchors = getBoundaryAnchors(target);

		for (BoundaryAnchor sourceEntry : sourceBoundaryAnchors) {
			FixPointAnchor potentialSourceAnchor = sourceEntry.anchor;
			for (BoundaryAnchor targetEntry : targetBoundaryAnchors) {
				FixPointAnchor potentialTargetAnchor = targetEntry.anchor;
				if(anchorVerifier.isAnchorsFromSameConnection(potentialSourceAnchor, potentialTargetAnchor)){
					sourceAnchor = potentialSourceAnchor;
					targetAnchor = potentialTargetAnchor;
					break;
				}
			}

			if(sourceAnchor != null){
				break;
			}
		}

		Point startP;
		Point endP;

		startP = GraphicsUtil.createPoint(sourceAnchor);
		endP = GraphicsUtil.createPoint(targetAnchor);
		Coordinate start = new Coordinate(startP.getX(), startP.getY());
		Coordinate end = new Coordinate(endP.getX(), endP.getY());
		ConnectionRoute route = new ConnectionRoute(this, allRoutes.size()+1, source,target);

		List<Coordinate> astarResult = aStar(start, end);
		List<Point> reducedAstar = calculateSegments(astarResult);
		route.getPoints().addAll(reducedAstar);

		return route;
	}


	@Override
	protected ContainerShape getCollision(Point p1, Point p2) {
		return super.getCollision(p1, p2);
	}

	@Override
	protected List<Connection> findCrossings(Point start, Point end) {
		return super.findCrossings(start, end);
	}

	protected Point getSegmentPoints() {
		LineSegment sourceEdges[] = GraphicsUtil.getEdges(source);
		sourceTopEdge = sourceEdges[0];
		sourceBottomEdge = sourceEdges[1];
		sourceLeftEdge = sourceEdges[2];
		sourceRightEdge = sourceEdges[3];

		LineSegment targetEdges[] = GraphicsUtil.getEdges(target);
		targetTopEdge = targetEdges[0];
		targetBottomEdge = targetEdges[1];
		targetLeftEdge = targetEdges[2];
		targetRightEdge = targetEdges[3];

		end = GraphicsUtil.createPoint(ffc.getEnd());
		Point middle = null;
		if (movedBendpoint!=null) {
			middle = movedBendpoint;
			findAllShapes();
			for (ContainerShape shape : allShapes) {
				if (GraphicsUtil.contains(shape, middle)) {
					middle = null;
					break;
				}
			}
		}
		return middle;
	}

	@Override
	protected List<ContainerShape> findAllShapes() {
		return super.findAllShapes();
	}


	protected ConnectionRoute calculateRoute(List<ConnectionRoute> allRoutes, Shape source, Point start, Point middle, Shape target, Point end, Orientation orientation) {

		ConnectionRoute route = new ConnectionRoute(this, allRoutes.size()+1, source,target);

		if (middle!=null) {
			List<Point> departure = calculateDeparture(source, start, middle);
			List<Point> approach = calculateApproach(middle, target, end);

			route.getPoints().addAll(departure);
			calculateEnroute(route, departure.get(departure.size()-1), middle, orientation);
			route.getPoints().add(middle);
			calculateEnroute(route, middle,approach.get(0),orientation);
			route.getPoints().addAll(approach);
		}
		else {
			List<Point> departure = calculateDeparture(source, start, end);
			List<Point> approach = calculateApproach(start, target, end);
			route.getPoints().addAll(departure);
			calculateEnroute(route, departure.get(departure.size()-1), approach.get(0), orientation);
			route.getPoints().addAll(approach);
		}

		if (route.isValid())
			allRoutes.add(route);

		return route;
	}

	private Point getVertMidpoint(Point start, Point end, double fract) {
		Point m = GraphicsUtil.createPoint(start);
		int d = (int)(fract * (double)(end.getY() - start.getY()));
		m.setY(start.getY()+d);
		return m;
	}

	private Point getHorzMidpoint(Point start, Point end, double fract) {
		Point m = GraphicsUtil.createPoint(start);
		int d = (int)(fract * (double)(end.getX() - start.getX()));
		m.setX(start.getX()+d);
		return m;
	}

	protected List<Point> calculateDeparture(Shape source, Point start, Point end) {
		AnchorLocation sourceEdge = AnchorUtil.findNearestBoundaryAnchor(source, start).locationType;
		List<Point> points = new ArrayList<Point>();

		Point p = GraphicsUtil.createPoint(start);
		Point m = end;

		switch (sourceEdge) {
		case TOP:
		case BOTTOM:
			for (;;) {
				m = getVertMidpoint(start,m,0.45);
				ContainerShape shape = getCollision(start,m);
				if (shape==null || Math.abs(m.getY()-start.getY())<=offset)
					break;
			}
			p.setY( m.getY() );
			break;
		case LEFT:
		case RIGHT:
			for (;;) {
				m = getHorzMidpoint(start,m,0.45);
				ContainerShape shape = getCollision(start,m);
				if (shape==null || Math.abs(m.getX()-start.getX())<=offset)
					break;
			}
			p.setX( m.getX() );
			break;
		default:
			return points;
		}

		points.add(start);
		points.add(p);

		return points;
	}

	protected List<Point> calculateApproach(Point start, Shape target, Point end) {
		AnchorLocation targetEdge = AnchorUtil.findNearestBoundaryAnchor(target, end).locationType;
		List<Point> points = new ArrayList<Point>();

		Point p = GraphicsUtil.createPoint(end);
		Point m = start;

		switch (targetEdge) {
		case TOP:
		case BOTTOM:
			for (;;) {
				m = getVertMidpoint(m,end,0.45);
				ContainerShape shape = getCollision(m,end);
				if (shape==null || shape==target || Math.abs(m.getY()-end.getY())<=offset)
					break;
			}
			p.setY( m.getY() );
			break;
		case LEFT:
		case RIGHT:
			for (;;) {
				m = getHorzMidpoint(m,end,0.45);
				ContainerShape shape = getCollision(m,end);
				if (shape==null || shape==target || Math.abs(m.getX()-end.getX())<=offset)
					break;
			}
			p.setX( m.getX() );
			break;
		default:
			return points;
		}

		points.add(p);
		points.add(end);

		return points;
	}

	Point createPoint(int x, int y) {
		return GraphicsUtil.createPoint(x, y); 
	}

	protected boolean calculateEnroute(ConnectionRoute route, Point start, Point end, Orientation orientation) {
		if (GraphicsUtil.pointsEqual(start, end))
			return false;

		Point p;

		// special case: if start and end can be connected with a horizontal or vertical line
		// check if there's a collision in the way. If so, we need to navigate around it.
		if (!GraphicsUtil.isSlanted(start,end)) {
			ContainerShape shape = getCollision(start,end);
			if (shape==null) {
				return true;
			}
		}



		int dx = Math.abs(end.getX() - start.getX());
		int dy = Math.abs(end.getY() - start.getY());
		if (orientation==Orientation.NONE) {
			if (dx>dy) {
				orientation = Orientation.HORIZONTAL;
			}
			else {
				orientation = Orientation.VERTICAL;
			}
		}

		if (orientation == Orientation.HORIZONTAL) {
			p = createPoint(end.getX(), start.getY());
			ContainerShape shape = getCollision(start,p);
			if (shape!=null) {


				DetourPoints detour = getDetourPoints(shape);
				// this should be a vertical segment - navigate around the shape
				// go up or down from here?
				boolean detourUp = end.getY() - start.getY() < 0;
				int dyTop = Math.abs(p.getY() - detour.topLeft.getY());
				int dyBottom = Math.abs(p.getY() - detour.bottomLeft.getY());
				if (dy<dyTop || dy<dyBottom)
					detourUp = dyTop < dyBottom;

				if (p.getX() > start.getX()) {
					p.setX( detour.topLeft.getX() );
					route.add(p);
					if (detourUp) {
						route.add(detour.topLeft);
						route.add(detour.topRight);
					}
					else {
						route.add(detour.bottomLeft);
						route.add(detour.bottomRight);
					}

				}
				else {
					p.setX( detour.topRight.getX() );
					route.add(p);
					if (detourUp) {
						route.add(detour.topRight);
						route.add(detour.topLeft);
					}
					else {
						route.add(detour.bottomRight);
						route.add(detour.bottomLeft);
					}

				}
				p = route.get(route.size()-1);
			}
			else
				route.add(p);
		}
		else {
			p = createPoint(start.getX(), end.getY());
			ContainerShape shape = getCollision(start,p);
			if (shape!=null) {

				DetourPoints detour = getDetourPoints(shape);
				// this should be a horizontal segment - navigate around the shape
				// go left or right from here?
				boolean detourLeft = end.getX() - start.getX() < 0;
				int dxLeft = Math.abs(p.getX() - detour.topLeft.getX());
				int dxRight = Math.abs(p.getX() - detour.topRight.getX());
				if (dx<dxLeft || dx<dxRight)
					detourLeft = dxLeft < dxRight;

				if (p.getY() > start.getY()) {
					p.setY( detour.topLeft.getY() );
					route.add(p);
					if (detourLeft) {
						// go around to the left
						route.add(detour.topLeft);
						route.add(detour.bottomLeft);
					}
					else {
						// go around to the right
						route.add(detour.topRight);
						route.add(detour.bottomRight);
					}
				}
				else {
					p.setY( detour.bottomLeft.getY() );
					route.add(p);
					if (detourLeft) {
						route.add(detour.bottomLeft);
						route.add(detour.topLeft);
					}
					else {
						route.add(detour.bottomRight);
						route.add(detour.topRight);
					}

				}
				p = route.get(route.size()-1);
			}
			else
				route.add(p);
		}

		if (route.isValid())
			calculateEnroute(route,p,end,Orientation.NONE);

		return route.isValid();
	}

	protected DetourPoints getDetourPoints(ContainerShape shape) {
		DetourPoints detour = new DetourPoints(shape, offset);
		if (allShapes==null)
			findAllShapes();

		for (int i=0; i<allShapes.size(); ++i) {
			ContainerShape s = allShapes.get(i);
			if (shape==s)
				continue;
			DetourPoints d = new DetourPoints(s, offset);
			if (detour.intersects(d) && !detour.contains(d)) {
				detour.merge(d);
				i = -1;
			}
		}

		return detour;
	}

	protected void finalizeConnection() {
	}

	protected boolean fixCollisions() {
		return false;
	}

	protected boolean calculateAnchors() {
		return false;
	}
	protected void updateConnection() {
		DIUtils.updateDIEdge(ffc);
	}

	protected List <BoundaryAnchor> getBoundaryAnchors(Shape s) {
		List <BoundaryAnchor> anchorList= new ArrayList<BoundaryAnchor> ();
		Iterator<Anchor> iterator = s.getAnchors().iterator();
		while (iterator.hasNext()) {
			Anchor anchor = iterator.next();
			String property = Graphiti.getPeService().getPropertyValue(anchor, AnchorUtil.BOUNDARY_FIXPOINT_ANCHOR);
			if (property != null && anchor instanceof FixPointAnchor) {
				BoundaryAnchor a = new BoundaryAnchor();
				a.anchor = (FixPointAnchor) anchor;
				a.locationType = AnchorLocation.getLocation(property);
				a.location = peService.getLocationRelativeToDiagram(anchor);
				anchorList.add(a);
			}
		}
		return anchorList;
	}

	protected List<Point> calculateSegments(List<Coordinate> points) {
		List<Point> result = new ArrayList<Point>();
		result.add(GraphicsUtil.createPoint(points.get(0).x, points.get(0).y));
		if(points.size()>2) {
			Iterator<Coordinate> iter = points.iterator();
			Coordinate prev = iter.next();
			while(iter.hasNext()) {
				Coordinate curr = iter.next();
				Coordinate next = iter.next();
				if(prev.x==curr.x&&curr.x!=next.x) result.add(GraphicsUtil.createPoint(curr.x, curr.y));
				if(prev.y==curr.y&&curr.y!=next.y) result.add(GraphicsUtil.createPoint(curr.x, curr.y));
				prev = iter.next();
			}
		}
		Coordinate last = points.get(points.size()-1);
		result.add(GraphicsUtil.createPoint(last.x, last.y));
		return result;
	}
	
	protected static class Coordinate {
		int x;
		int y;
		
		public Coordinate(int x, int y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + x;
			result = prime * result + y;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Coordinate other = (Coordinate) obj;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}
	}

	//Based on https://en.wikipedia.org/wiki/A*_search_algorithm
	protected List<Coordinate> aStar(Coordinate start, Coordinate goal) {
		Set<Coordinate> closedset = new HashSet<Coordinate>();
		Set<Coordinate> openset = new HashSet<Coordinate>();
		openset.add(start);
		Map<Coordinate, Coordinate> came_from = new HashMap<Coordinate, Coordinate>();

		Map<Coordinate, Integer> g_score = new HashMap<Coordinate, Integer>();
		g_score.put(start, 0);

		Map<Coordinate, Integer> f_score = new HashMap<Coordinate, Integer>();
		f_score.put(start, g_score.getOrDefault(start, Integer.MAX_VALUE)+heuristicCostEstimate(start, goal));

		while(!openset.isEmpty()) {
			Coordinate current = lowestFScore(f_score);
			if(current.equals(goal)) return reconstructPath(came_from, goal);

			openset.remove(current);
			closedset.add(current);
			for(Coordinate neighbor:neighborNodes(current)) {
				if(closedset.contains(neighbor)) continue;
				ContainerShape hadCollision = getCollision(GraphicsUtil.createPoint(neighbor.x, neighbor.y),
						GraphicsUtil.createPoint(neighbor.x, neighbor.y));
				if(hadCollision!=null) {
					closedset.add(neighbor);
					continue;
				}

				int tentative_g_score = g_score.getOrDefault(current, Integer.MAX_VALUE)+1; //the distance between current and neighbor is always 1

				if(!openset.contains(neighbor) || tentative_g_score < g_score.getOrDefault(current, Integer.MAX_VALUE)) {
					came_from.put(neighbor, current);
					g_score.put(neighbor, tentative_g_score);
					f_score.put(neighbor, tentative_g_score + heuristicCostEstimate(neighbor, goal));
					openset.add(neighbor);
				}
			}
		}

		List<Coordinate> alterResult = new ArrayList<Coordinate>();
		alterResult.add(start);
		alterResult.add(goal);
		return alterResult;
	}

	protected int heuristicCostEstimate(Coordinate a, Coordinate b) {
		return Math.abs(a.x-b.x)+Math.abs(a.y-b.y);
	}

	protected Coordinate lowestFScore(Map<Coordinate, Integer> f_score) {
		Entry<Coordinate, Integer> min = null;
		for(Entry<Coordinate, Integer> entry : f_score.entrySet()) {
			if(min==null || min.getValue() > entry.getValue()) {
				min = entry;
			}
		}
		return min.getKey();
	}

	protected List<Coordinate> neighborNodes(Coordinate current) {
		List<Coordinate> list = new ArrayList<Coordinate>();
		list.add(new Coordinate(current.x+1, current.y));
		list.add(new Coordinate(current.x-1, current.y));
		list.add(new Coordinate(current.x, current.y+1));
		list.add(new Coordinate(current.x, current.y-1));
		return list;
	}

	protected List<Coordinate> reconstructPath(Map<Coordinate, Coordinate> came_from, Coordinate current) {
		List<Coordinate> totalPath = new ArrayList<Coordinate>();
		totalPath.add(current);
		while(came_from.containsKey(current)) {
			current = came_from.get(current);
			totalPath.add(current);
		}
		return totalPath;
	}
}
