package com.makar.utopia.boundary;

import java.util.Arrays;
import java.util.List;

/** A simple, closed polygon in world X/Z coordinates. The boundary belongs to the polygon. */
public record Polygon(List<Point> vertices) {
    public static final int MAX_VERTICES = 128;
    public static final double MAX_COORDINATE = 29_999_984;
    private static final double EPSILON = 1e-7;

    public record Point(double x, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(z)
                    || Math.abs(x) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE) {
                throw new IllegalArgumentException("Polygon coordinates must be finite and within the Minecraft world");
            }
        }
    }

    public Polygon {
        vertices = List.copyOf(vertices);
        int count = vertices.size();
        if (count < 3 || count > MAX_VERTICES) {
            throw new IllegalArgumentException("A polygon needs 3 to " + MAX_VERTICES + " vertices");
        }
        double area = 0;
        Point origin = vertices.get(0);
        for (int i = 0; i < count; i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % count);
            Point c = vertices.get((i + 2) % count);
            if (Math.hypot(b.x - a.x, b.z - a.z) < EPSILON || onSegment(a, b, c.x, c.z)
                    || onSegment(b, c, a.x, a.z)) {
                throw new IllegalArgumentException("Repeated vertex or overlapping adjacent edges at vertex " + i);
            }
            area += cross(origin, a, b.x, b.z);
            for (int j = i + 2; j < count; j++) {
                if (i == 0 && j == count - 1) continue;
                Point d = vertices.get(j);
                Point e = vertices.get((j + 1) % count);
                if (segmentsIntersect(a, b, d, e)) {
                    throw new IllegalArgumentException("Polygon edges " + i + " and " + j + " intersect");
                }
            }
        }
        if (Math.abs(area) < EPSILON) throw new IllegalArgumentException("Polygon has no area");
    }

    public boolean contains(double x, double z) {
        boolean inside = false;
        for (int i = 0; i < vertices.size(); i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            if (onSegment(a, b, x, z)) return true;
            if ((a.z > z) != (b.z > z) && x < crossingX(a, b, z)) inside = !inside;
        }
        return inside;
    }

    /** True for touching borders, overlapping interiors or containment. */
    public boolean intersects(Polygon other) {
        Point first = other.vertices.get(0);
        Point own = vertices.get(0);
        if (contains(first.x, first.z) || other.contains(own.x, own.z)) return true;
        for (int i = 0; i < vertices.size(); i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            for (int j = 0; j < other.vertices.size(); j++) {
                Point c = other.vertices.get(j);
                Point d = other.vertices.get((j + 1) % other.vertices.size());
                if (segmentsIntersect(a, b, c, d)) return true;
            }
        }
        return false;
    }

    /** Checks the entire rectangle, including concave intrusions between its corners. */
    public boolean containsRectangle(double minX, double minZ, double maxX, double maxZ) {
        if (!contains(minX, minZ) || !contains(minX, maxZ) || !contains(maxX, minZ) || !contains(maxX, maxZ)) {
            return false;
        }
        for (int i = 0; i < vertices.size(); i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            if (Math.max(a.x, b.x) <= minX || Math.min(a.x, b.x) >= maxX
                    || Math.max(a.z, b.z) <= minZ || Math.min(a.z, b.z) >= maxZ) continue;
            if (a.x == b.x) return false;
            double first = (minX - a.x) / (b.x - a.x);
            double last = (maxX - a.x) / (b.x - a.x);
            double from = a.z + Math.max(0, Math.min(first, last)) * (b.z - a.z);
            double to = a.z + Math.min(1, Math.max(first, last)) * (b.z - a.z);
            if (Math.max(from, to) > minZ && Math.min(from, to) < maxZ) return false;
        }
        return true;
    }

    /** Sorted X intersections of a horizontal scanline, paired by the even-odd fill rule. */
    public int scanline(double z, double[] intersections) {
        int count = 0;
        for (int i = 0; i < vertices.size(); i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            if ((a.z > z) != (b.z > z)) intersections[count++] = crossingX(a, b, z);
        }
        Arrays.sort(intersections, 0, count);
        return count;
    }

    /** Tests entire edges, including an edge that exits and re-enters at concave outer vertices. */
    public boolean encloses(Polygon inner) {
        double[] cuts = new double[vertices.size() + 2];
        for (int i = 0; i < inner.vertices.size(); i++) {
            Point a = inner.vertices.get(i);
            Point b = inner.vertices.get((i + 1) % inner.vertices.size());
            if (!contains(a.x, a.z)) return false;
            double dx = b.x - a.x;
            double dz = b.z - a.z;
            double squaredLength = dx * dx + dz * dz;
            cuts[0] = 0;
            cuts[1] = 1;
            int count = 2;
            for (int j = 0; j < vertices.size(); j++) {
                Point c = vertices.get(j);
                Point d = vertices.get((j + 1) % vertices.size());
                if (crosses(a, b, c, d)) return false;
                if (onSegment(a, b, c.x, c.z)) {
                    cuts[count++] = Math.max(0, Math.min(1, ((c.x - a.x) * dx + (c.z - a.z) * dz) / squaredLength));
                }
            }
            Arrays.sort(cuts, 0, count);
            for (int j = 1; j < count; j++) {
                double t = (cuts[j - 1] + cuts[j]) / 2;
                if (!contains(a.x + t * dx, a.z + t * dz)) return false;
            }
        }
        return true;
    }

    private static double crossingX(Point a, Point b, double z) {
        return a.x + (z - a.z) * (b.x - a.x) / (b.z - a.z);
    }

    private static boolean crosses(Point a, Point b, Point c, Point d) {
        return side(a, b, c.x, c.z) * side(a, b, d.x, d.z) < 0
                && side(c, d, a.x, a.z) * side(c, d, b.x, b.z) < 0;
    }

    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        return crosses(a, b, c, d) || onSegment(a, b, c.x, c.z) || onSegment(a, b, d.x, d.z)
                || onSegment(c, d, a.x, a.z) || onSegment(c, d, b.x, b.z);
    }

    private static boolean onSegment(Point a, Point b, double x, double z) {
        return x >= Math.min(a.x, b.x) - EPSILON && x <= Math.max(a.x, b.x) + EPSILON
                && z >= Math.min(a.z, b.z) - EPSILON && z <= Math.max(a.z, b.z) + EPSILON
                && side(a, b, x, z) == 0;
    }

    private static int side(Point a, Point b, double x, double z) {
        double distance = cross(a, b, x, z);
        double tolerance = EPSILON * Math.hypot(b.x - a.x, b.z - a.z);
        if (Math.abs(distance) <= tolerance) return 0;
        return distance > 0 ? 1 : -1;
    }

    private static double cross(Point a, Point b, double x, double z) {
        return (b.x - a.x) * (z - a.z) - (b.z - a.z) * (x - a.x);
    }
}
