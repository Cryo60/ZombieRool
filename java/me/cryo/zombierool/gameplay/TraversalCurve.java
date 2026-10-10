package me.cryo.zombierool.gameplay;

/** Two quadratic segments meeting at the chosen apex with a horizontal tangent. */
public final class TraversalCurve {
    public record Point(double x, double y, double z) {}
    private TraversalCurve() {}

    public static Point sample(Point entry, Point apex, Point exit, double progress) {
        if (progress <= 0) return entry;
        if (progress >= 1) return exit;
        double up = Math.hypot(apex.x - entry.x, apex.z - entry.z);
        double down = Math.hypot(exit.x - apex.x, exit.z - apex.z);
        double split = Math.max(.1, Math.min(.9, up / Math.max(.001, up + down)));
        Point a, b, c;
        double t;
        if (progress <= split) {
            a = entry; c = apex; t = progress / split;
            b = new Point((entry.x + apex.x) / 2, apex.y, (entry.z + apex.z) / 2);
        } else {
            a = apex; c = exit; t = (progress - split) / (1 - split);
            b = new Point((apex.x + exit.x) / 2, apex.y, (apex.z + exit.z) / 2);
        }
        double u = 1 - t;
        return new Point(u*u*a.x + 2*u*t*b.x + t*t*c.x,
                u*u*a.y + 2*u*t*b.y + t*t*c.y,
                u*u*a.z + 2*u*t*b.z + t*t*c.z);
    }
}
