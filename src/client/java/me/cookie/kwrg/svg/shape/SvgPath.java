package me.cookie.kwrg.svg.shape;

import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

public final class SvgPath {

    private static final int CUBIC = 1;
    private static final int QUADRATIC = 2;
    private static final double QUARTER_TURN = Math.PI / 2.0;

    private SvgPath() {
    }

    public static Path2D.Double parse(String d) {
        Path2D.Double path = new Path2D.Double();
        if (d == null || d.isBlank()) {
            return path;
        }
        new Parser(d, path).run();
        return path;
    }

    private static final class Parser {

        private final String text;
        private final int length;
        private final Path2D.Double path;
        private final double[] values = new double[6];
        private int index;
        private double currentX;
        private double currentY;
        private double startX;
        private double startY;
        private double controlX;
        private double controlY;
        private int controlKind;
        private char command;
        private boolean active;

        Parser(String text, Path2D.Double path) {
            this.text = text;
            this.length = text.length();
            this.path = path;
        }

        void run() {
            while (true) {
                skipSeparators();
                if (index >= length) {
                    return;
                }
                char ch = text.charAt(index);
                if (isCommand(ch)) {
                    index++;
                    if (ch == 'Z' || ch == 'z') {
                        close();
                        continue;
                    }
                    command = ch;
                } else if (command == 0 || !isNumberStart(ch)) {
                    index++;
                    continue;
                }
                if (!apply(command)) {
                    return;
                }
                if (command == 'M') {
                    command = 'L';
                } else if (command == 'm') {
                    command = 'l';
                }
            }
        }

        private boolean apply(char cmd) {
            return switch (cmd) {
                case 'M' -> move(false);
                case 'm' -> move(true);
                case 'L' -> line(false);
                case 'l' -> line(true);
                case 'H' -> horizontal(false);
                case 'h' -> horizontal(true);
                case 'V' -> vertical(false);
                case 'v' -> vertical(true);
                case 'C' -> cubic(false, false);
                case 'c' -> cubic(true, false);
                case 'S' -> cubic(false, true);
                case 's' -> cubic(true, true);
                case 'Q' -> quadratic(false, false);
                case 'q' -> quadratic(true, false);
                case 'T' -> quadratic(false, true);
                case 't' -> quadratic(true, true);
                case 'A' -> arc(false);
                case 'a' -> arc(true);
                default -> false;
            };
        }

        private void close() {
            if (active) {
                path.closePath();
            }
            currentX = startX;
            currentY = startY;
            controlKind = 0;
            command = 0;
        }

        private boolean move(boolean relative) {
            if (!readArguments(2)) {
                return false;
            }
            double x = relative ? values[0] + currentX : values[0];
            double y = relative ? values[1] + currentY : values[1];
            path.moveTo(x, y);
            currentX = x;
            currentY = y;
            startX = x;
            startY = y;
            controlKind = 0;
            active = true;
            return true;
        }

        private boolean line(boolean relative) {
            if (!readArguments(2)) {
                return false;
            }
            double x = relative ? values[0] + currentX : values[0];
            double y = relative ? values[1] + currentY : values[1];
            path.lineTo(x, y);
            currentX = x;
            currentY = y;
            controlKind = 0;
            active = true;
            return true;
        }

        private boolean horizontal(boolean relative) {
            if (!readArguments(1)) {
                return false;
            }
            double x = relative ? values[0] + currentX : values[0];
            path.lineTo(x, currentY);
            currentX = x;
            controlKind = 0;
            active = true;
            return true;
        }

        private boolean vertical(boolean relative) {
            if (!readArguments(1)) {
                return false;
            }
            double y = relative ? values[0] + currentY : values[0];
            path.lineTo(currentX, y);
            currentY = y;
            controlKind = 0;
            active = true;
            return true;
        }

        private boolean cubic(boolean relative, boolean smooth) {
            if (!readArguments(smooth ? 4 : 6)) {
                return false;
            }
            int offset = smooth ? 0 : 2;
            double x1 = smooth ? reflectX(CUBIC) : values[0];
            double y1 = smooth ? reflectY(CUBIC) : values[1];
            double x2 = values[offset];
            double y2 = values[offset + 1];
            double x = values[offset + 2];
            double y = values[offset + 3];
            if (relative) {
                x2 += currentX;
                y2 += currentY;
                x += currentX;
                y += currentY;
            }
            path.curveTo(x1, y1, x2, y2, x, y);
            controlX = x2;
            controlY = y2;
            controlKind = CUBIC;
            currentX = x;
            currentY = y;
            active = true;
            return true;
        }

        private boolean quadratic(boolean relative, boolean smooth) {
            if (!readArguments(smooth ? 2 : 4)) {
                return false;
            }
            int offset = smooth ? 0 : 2;
            double x1 = smooth ? reflectX(QUADRATIC) : values[0];
            double y1 = smooth ? reflectY(QUADRATIC) : values[1];
            double x = values[offset];
            double y = values[offset + 1];
            if (relative) {
                x += currentX;
                y += currentY;
            }
            path.quadTo(x1, y1, x, y);
            controlX = x1;
            controlY = y1;
            controlKind = QUADRATIC;
            currentX = x;
            currentY = y;
            active = true;
            return true;
        }

        private double reflectX(int kind) {
            return controlKind == kind ? currentX * 2.0 - controlX : currentX;
        }

        private double reflectY(int kind) {
            return controlKind == kind ? currentY * 2.0 - controlY : currentY;
        }

        private boolean arc(boolean relative) {
            if (!readArguments(3)) {
                return false;
            }
            double radiusX = values[0];
            double radiusY = values[1];
            double rotation = values[2];
            int largeArc = readFlag();
            if (largeArc < 0) {
                return false;
            }
            int sweep = readFlag();
            if (sweep < 0) {
                return false;
            }
            if (!readArguments(2)) {
                return false;
            }
            double x = relative ? values[0] + currentX : values[0];
            double y = relative ? values[1] + currentY : values[1];
            appendArc(radiusX, radiusY, rotation, largeArc == 1, sweep == 1, x, y);
            currentX = x;
            currentY = y;
            controlKind = 0;
            active = true;
            return true;
        }

        private void appendArc(double radiusX, double radiusY, double rotation, boolean largeArc, boolean sweep, double x, double y) {
            double x1 = currentX;
            double y1 = currentY;
            if (x1 == x && y1 == y) {
                return;
            }
            double rx = Math.abs(radiusX);
            double ry = Math.abs(radiusY);
            if (rx == 0.0 || ry == 0.0) {
                path.lineTo(x, y);
                return;
            }
            double phi = Math.toRadians(rotation % 360.0);
            double cosPhi = Math.cos(phi);
            double sinPhi = Math.sin(phi);
            double dx = (x1 - x) / 2.0;
            double dy = (y1 - y) / 2.0;
            double dxp = cosPhi * dx + sinPhi * dy;
            double dyp = -sinPhi * dx + cosPhi * dy;
            double lambda = dxp * dxp / (rx * rx) + dyp * dyp / (ry * ry);
            if (lambda > 1.0) {
                double scale = Math.sqrt(lambda);
                rx *= scale;
                ry *= scale;
            }
            double rxSquared = rx * rx;
            double rySquared = ry * ry;
            double numerator = rxSquared * rySquared - rxSquared * dyp * dyp - rySquared * dxp * dxp;
            double denominator = rxSquared * dyp * dyp + rySquared * dxp * dxp;
            double coefficient = 0.0;
            if (denominator > 0.0) {
                coefficient = Math.sqrt(Math.max(0.0, numerator / denominator));
                if (largeArc == sweep) {
                    coefficient = -coefficient;
                }
            }
            double centerXp = coefficient * (rx * dyp / ry);
            double centerYp = -coefficient * (ry * dxp / rx);
            double centerX = cosPhi * centerXp - sinPhi * centerYp + (x1 + x) / 2.0;
            double centerY = sinPhi * centerXp + cosPhi * centerYp + (y1 + y) / 2.0;
            double ux = (dxp - centerXp) / rx;
            double uy = (dyp - centerYp) / ry;
            double vx = (-dxp - centerXp) / rx;
            double vy = (-dyp - centerYp) / ry;
            double startAngle = Math.atan2(uy, ux);
            double delta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
            if (sweep && delta < 0.0) {
                delta += Math.PI * 2.0;
            } else if (!sweep && delta > 0.0) {
                delta -= Math.PI * 2.0;
            }
            int segments = (int) Math.ceil(Math.abs(delta) / QUARTER_TURN);
            if (segments < 1) {
                segments = 1;
            }
            double step = delta / segments;
            AffineTransform transform = new AffineTransform();
            transform.translate(centerX, centerY);
            transform.rotate(phi);
            transform.scale(rx, ry);
            double angle = startAngle;
            for (int i = 0; i < segments; i++) {
                Arc2D segment = new Arc2D.Double(-1.0, -1.0, 2.0, 2.0, Math.toDegrees(-angle), Math.toDegrees(-step), Arc2D.OPEN);
                path.append(segment.getPathIterator(transform), true);
                angle += step;
            }
        }

        private boolean readArguments(int count) {
            for (int i = 0; i < count; i++) {
                skipSeparators();
                double value = readNumber();
                if (Double.isNaN(value)) {
                    return false;
                }
                values[i] = value;
            }
            return true;
        }

        private double readNumber() {
            int start = index;
            if (index < length) {
                char sign = text.charAt(index);
                if (sign == '+' || sign == '-') {
                    index++;
                }
            }
            boolean digits = false;
            while (index < length && isDigit(text.charAt(index))) {
                index++;
                digits = true;
            }
            if (index < length && text.charAt(index) == '.') {
                index++;
                while (index < length && isDigit(text.charAt(index))) {
                    index++;
                    digits = true;
                }
            }
            if (!digits) {
                index = start;
                return Double.NaN;
            }
            if (index < length) {
                char marker = text.charAt(index);
                if (marker == 'e' || marker == 'E') {
                    int mark = index;
                    index++;
                    if (index < length) {
                        char sign = text.charAt(index);
                        if (sign == '+' || sign == '-') {
                            index++;
                        }
                    }
                    boolean exponent = false;
                    while (index < length && isDigit(text.charAt(index))) {
                        index++;
                        exponent = true;
                    }
                    if (!exponent) {
                        index = mark;
                    }
                }
            }
            try {
                return Double.parseDouble(text.substring(start, index));
            } catch (NumberFormatException exception) {
                return Double.NaN;
            }
        }

        private int readFlag() {
            skipSeparators();
            if (index >= length) {
                return -1;
            }
            char ch = text.charAt(index);
            if (ch == '0' || ch == '1') {
                index++;
                return ch - '0';
            }
            return -1;
        }

        private void skipSeparators() {
            while (index < length) {
                char ch = text.charAt(index);
                if (ch == ',' || Character.isWhitespace(ch)) {
                    index++;
                } else {
                    return;
                }
            }
        }

        private static boolean isCommand(char ch) {
            return switch (ch) {
                case 'M', 'm', 'L', 'l', 'H', 'h', 'V', 'v', 'C', 'c', 'S', 's', 'Q', 'q', 'T', 't', 'A', 'a', 'Z', 'z' -> true;
                default -> false;
            };
        }

        private static boolean isDigit(char ch) {
            return ch >= '0' && ch <= '9';
        }

        private static boolean isNumberStart(char ch) {
            return ch == '+' || ch == '-' || ch == '.' || isDigit(ch);
        }
    }
}
