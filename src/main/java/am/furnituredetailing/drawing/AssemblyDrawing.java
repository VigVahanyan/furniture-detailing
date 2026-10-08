package am.furnituredetailing.drawing;

import am.furnituredetailing.design.Layout;
import am.furnituredetailing.design.ModuleSpec;

import java.util.*;

/**
 * Technical front elevation of the whole assembly as SVG (units: mm).
 *
 * <p>Left: carcass with fronts removed — side panels, top, bottom, plinth and legs at real panel thickness,
 * partitions, shelves, drawer boxes and rail. Right: the same with doors and drawer fronts, hinge-side marks
 * and handles. Dimension chains run along every module boundary; a spec table lists each module.
 */
public final class AssemblyDrawing {

    private AssemblyDrawing() {
    }

    public static String render(List<ModuleSpec> modules, int thickness, String title) {
        List<ModuleSpec> mods = Layout.place(modules.stream().map(ModuleSpec::normalized).toList());
        if (mods.isEmpty()) throw new IllegalArgumentException("no modules");
        return new Ctx(mods, thickness <= 0 ? 18 : thickness, title).draw();
    }

    private static final class Ctx {
        final List<ModuleSpec> mods;
        final int t;
        final String title;
        final StringBuilder sb = new StringBuilder(64_000);

        final int minX, minY, totW, totH, maxD;
        final double f, sw, thin, m, dimL, gap, dimR, ax, bx, top, floor, svgW;

        Ctx(List<ModuleSpec> mods, int t, String title) {
            this.mods = mods;
            this.t = t;
            minX = mods.stream().mapToInt(ModuleSpec::x).min().orElse(0);
            minY = mods.stream().mapToInt(ModuleSpec::y).min().orElse(0);
            totW = mods.stream().mapToInt(md -> md.x() + md.W()).max().orElse(0) - minX;
            totH = mods.stream().mapToInt(md -> md.y() + md.H()).max().orElse(0) - minY;
            maxD = mods.stream().mapToInt(ModuleSpec::D).max().orElse(0);
            this.title = title == null || title.isBlank() ? "Сборка " + totW + " × " + totH + " × " + maxD + " мм" : title.strip();

            f = Math.max(22, Math.min(80, Math.max(totW, totH) / 62.0));
            sw = f / 16;
            thin = sw * 0.55;
            m = f * 1.6;
            dimL = f * 6.2;
            gap = f * 5;
            dimR = f * 3.6;
            ax = m + dimL;
            bx = ax + totW + gap;
            top = m + f * 3.2 + f * 1.8;
            floor = top + totH;
            svgW = Math.max(bx + totW + dimR + m, f * 62);
        }

        String draw() {
            double tableTop = floor + f * 6.4;
            double rowH = f * 1.75;
            double tableH = rowH * (mods.size() + 1);
            double svgH = tableTop + tableH + f * 2.4 + m;
            double px = 1800 / svgW;

            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(n(svgW)).append(' ').append(n(svgH))
              .append("\" width=\"").append(n(svgW * px)).append("\" height=\"").append(n(svgH * px)).append("\">\n");
            style();
            sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");

            text(m, m + f * 1.3, f * 1.35, "start", "tt", title);
            text(m, m + f * 2.6, f * 0.8, "start", "st",
                    "ЛДСП " + t + " мм · глубина " + maxD + " мм · модулей " + mods.size() + " · размеры в мм · конструкторская схема");

            text(ax, top - f * 0.9, f * 0.85, "start", "lb", "ВИД СПЕРЕДИ — КОРПУС (фасады сняты)");
            text(bx, top - f * 0.9, f * 0.85, "start", "lb", "ВИД СПЕРЕДИ — С ФАСАДАМИ");

            for (int i = 0; i < mods.size(); i++) module(mods.get(i), ax, false, i + 1);
            for (int i = 0; i < mods.size(); i++) module(mods.get(i), bx, true, i + 1);
            floorLine(ax);
            floorLine(bx);
            dimensions();
            table(tableTop, rowH);
            text(m, tableTop + tableH + f * 1.4, f * 0.7, "start", "st",
                    "Схема для расчёта деталей и раскроя. Зеркала, крючки, мягкие элементы и фрезеровка не показаны.");
            sb.append("</svg>\n");
            return sb.toString();
        }

        // ---------- geometry ----------

        double X(double mmX, double ox) { return ox + mmX - minX; }

        double Y(double mmY) { return floor - (mmY - minY); }

        /** Rectangle given by its lower-left corner in floor coordinates. */
        void rect(double ox, double x, double y, double w, double h, String cls) {
            if (w <= 0 || h <= 0) return;
            sb.append("<rect class=\"").append(cls).append("\" x=\"").append(n(X(x, ox))).append("\" y=\"").append(n(Y(y + h)))
              .append("\" width=\"").append(n(w)).append("\" height=\"").append(n(h)).append("\"/>\n");
        }

        void line(double x1, double y1, double x2, double y2, String cls) {
            sb.append("<line class=\"").append(cls).append("\" x1=\"").append(n(x1)).append("\" y1=\"").append(n(y1))
              .append("\" x2=\"").append(n(x2)).append("\" y2=\"").append(n(y2)).append("\"/>\n");
        }

        void module(ModuleSpec md, double ox, boolean fronts, int no) {
            int W = md.W(), H = md.H(), x = md.x(), yb = md.y();
            int legH = "legs".equals(md.base()) ? md.legH() : 0;
            int plH = "plinth".equals(md.base()) ? md.plinthH() : 0;
            int bodyH = H - legH, inW = W - 2 * t;
            int nDr = md.drawers(), nDoor = md.doors(), dh = md.dh(), nP = md.partitions(), nS = md.shelves();
            int drawerZone = nDr * dh;
            double innerBot = yb + legH + plH + t, innerTop = yb + H - t;

            // back panel (or open back) behind everything
            rect(ox, x + t, yb + legH + plH, inW, bodyH - plH, md.back() ? "bk" : "nb");
            // carcass
            rect(ox, x, yb + legH, t, bodyH, "pn");
            rect(ox, x + W - t, yb + legH, t, bodyH, "pn");
            rect(ox, x + t, yb + H - t, inW, t, "pn");
            rect(ox, x + t, yb + legH + plH, inW, t, "pn");
            if (plH > 0) rect(ox, x + t, yb + legH, inW, plH, "pl");
            if (legH > 0) {
                List<Double> lx = new ArrayList<>(List.of(x + 30.0, x + W - 70.0));
                if (W > 900) lx.add(x + W / 2.0 - 20);
                for (double l : lx) rect(ox, l, yb, 40, legH, "leg");
            }

            double zoneBot = innerBot;
            if (nDr > 0 && nDoor > 0) {
                rect(ox, x + t, innerBot + drawerZone, inW, t, "pn");
                zoneBot = innerBot + drawerZone + t;
            }
            if (nDr > 0) {
                double avail = nDoor > 0 ? drawerZone : innerTop - innerBot;
                double step = Math.min(dh, avail / nDr);
                for (int i = 0; i < nDr; i++) {
                    double h = Math.max(60, Math.min(step - 40, dh - 60));
                    rect(ox, x + t + 13, innerBot + i * step + 15, inW - 26, h, "bx");
                }
            }
            boolean showInner = !(nDr > 0 && nDoor == 0);
            if (showInner && innerTop - zoneBot > 40) {
                double secW = (inW - nP * t) / (double) (nP + 1);
                for (int p = 1; p <= nP; p++) {
                    rect(ox, x + t + p * secW + (p - 1) * t, zoneBot, t, innerTop - zoneBot, "pn");
                }
                for (int s = 0; s <= nP; s++) {
                    double sx = x + t + s * (secW + t);
                    for (int k = 1; k <= nS; k++) {
                        double sy = zoneBot + (innerTop - zoneBot) * k / (nS + 1) - t / 2.0;
                        rect(ox, sx, sy, secW, t, "pn");
                    }
                    if (md.rod() && nDoor > 0) {
                        double ry = Y(innerTop - (nS > 0 ? Math.min(70, (innerTop - zoneBot) / (nS + 1) / 3) : 70));
                        double x1 = X(sx + 25, ox), x2 = X(sx + secW - 25, ox);
                        line(x1, ry, x2, ry, "rod");
                        dot(x1, ry, sw * 2.4);
                        dot(x2, ry, sw * 2.4);
                    }
                }
            }

            if (fronts) fronts(md, ox, x, yb, W, legH, plH, bodyH, nDr, nDoor, dh, drawerZone);

            // module number
            double cx = X(x + W / 2.0, ox), cy = Y(yb + H / 2.0);
            double r = f * 0.78;
            sb.append("<circle class=\"no\" cx=\"").append(n(cx)).append("\" cy=\"").append(n(cy)).append("\" r=\"").append(n(r)).append("\"/>\n");
            text(cx, cy + f * 0.32, f * 0.9, "middle", "not", String.valueOf(no));
        }

        void fronts(ModuleSpec md, double ox, int x, int yb, int W, int legH, int plH, int bodyH,
                    int nDr, int nDoor, int dh, int drawerZone) {
            double fBot = yb + legH + plH + 2;
            double fz = bodyH - plH - 4;
            for (int i = 0; i < nDr; i++) {
                double fy = fBot + i * dh, fh = dh - 3;
                rect(ox, x + 2, fy, W - 4, fh, "fr");
                double hw = Math.min(160, (W - 4) * 0.3), cx = X(x + W / 2.0, ox), cy = Y(fy + fh / 2);
                line(cx - hw / 2, cy, cx + hw / 2, cy, "hd");
            }
            if (nDoor > 0 && fz - drawerZone > 100) {
                double dH = fz - drawerZone, dW = (W - 4 - 3.0 * (nDoor - 1)) / nDoor, dy = fBot + drawerZone;
                double handleY = Math.max(dy + 110, Math.min(dy + dH - 110, 1050));
                if (dH < 260) handleY = dy + dH / 2;
                for (int d = 0; d < nDoor; d++) {
                    double dx = x + 2 + d * (dW + 3);
                    rect(ox, dx, dy, dW, dH, "fr");
                    boolean hingeLeft = nDoor == 1 || d % 2 == 0;
                    // opening mark: apex on the hinge side
                    double apexX = X(hingeLeft ? dx : dx + dW, ox), farX = X(hingeLeft ? dx + dW : dx, ox);
                    sb.append("<polyline class=\"op\" points=\"").append(n(farX)).append(',').append(n(Y(dy + dH))).append(' ')
                      .append(n(apexX)).append(',').append(n(Y(dy + dH / 2))).append(' ')
                      .append(n(farX)).append(',').append(n(Y(dy))).append("\"/>\n");
                    double hx = X(hingeLeft ? dx + dW - 45 : dx + 45, ox);
                    double hl = Math.min(160, dH * 0.18);
                    line(hx, Y(handleY) - hl / 2, hx, Y(handleY) + hl / 2, "hd");
                }
            }
        }

        void dot(double cx, double cy, double r) {
            sb.append("<circle class=\"rdot\" cx=\"").append(n(cx)).append("\" cy=\"").append(n(cy)).append("\" r=\"").append(n(r)).append("\"/>\n");
        }

        void floorLine(double ox) {
            double y = Y(minY);
            line(ox - f * 0.8, y, ox + totW + f * 0.8, y, "flr");
        }

        // ---------- dimensions ----------

        void dimensions() {
            TreeSet<Integer> xs = new TreeSet<>(), ys = new TreeSet<>();
            for (ModuleSpec md : mods) {
                xs.add(md.x() - minX);
                xs.add(md.x() + md.W() - minX);
                ys.add(md.y() - minY);
                ys.add(md.y() + md.H() - minY);
            }
            // carcass view: chains + overall
            double y1 = floor + f * 2.2, y2 = floor + f * 4.6;
            chainH(ax, xs, y1);
            if (xs.size() > 2) hdim(ax, 0, totW, y2);
            double x1 = ax - f * 2.4, x2 = ax - f * 4.9;
            chainV(ys, x1);
            if (ys.size() > 2) vdim(0, totH, x2);
            // facade view: overall only
            hdim(bx, 0, totW, y1);
            vdim2(0, totH, bx + totW + f * 2.2);
        }

        void chainH(double ox, TreeSet<Integer> xs, double yl) {
            Integer prev = null;
            for (int v : xs) {
                if (prev != null) hdim(ox, prev, v, yl);
                prev = v;
            }
        }

        void chainV(TreeSet<Integer> ys, double xl) {
            Integer prev = null;
            for (int v : ys) {
                if (prev != null) vdim(prev, v, xl);
                prev = v;
            }
        }

        /** Horizontal dimension between a and b (mm from the assembly's left edge) on line yl. */
        void hdim(double ox, int a, int b, double yl) {
            double xa = ox + a, xb = ox + b;
            line(xa, floor + f * 0.35, xa, yl + f * 0.35, "ext");
            line(xb, floor + f * 0.35, xb, yl + f * 0.35, "ext");
            line(xa, yl, xb, yl, "dim");
            tick(xa, yl);
            tick(xb, yl);
            if (b - a >= f * 1.7) text((xa + xb) / 2, yl - f * 0.3, f * 0.82, "middle", "dt", String.valueOf(b - a));
        }

        /** Vertical dimension on the left of the carcass view. */
        void vdim(int a, int b, double xl) {
            double ya = floor - a, yb = floor - b;
            line(ax - f * 0.35, ya, xl - f * 0.35, ya, "ext");
            line(ax - f * 0.35, yb, xl - f * 0.35, yb, "ext");
            line(xl, ya, xl, yb, "dim");
            tick(xl, ya);
            tick(xl, yb);
            if (b - a >= f * 1.7) vtext(xl - f * 0.3, (ya + yb) / 2, String.valueOf(b - a));
        }

        /** Vertical dimension on the right of the facade view. */
        void vdim2(int a, int b, double xl) {
            double ya = floor - a, yb = floor - b, edge = bx + totW;
            line(edge + f * 0.35, ya, xl + f * 0.35, ya, "ext");
            line(edge + f * 0.35, yb, xl + f * 0.35, yb, "ext");
            line(xl, ya, xl, yb, "dim");
            tick(xl, ya);
            tick(xl, yb);
            vtext(xl - f * 0.3, (ya + yb) / 2, String.valueOf(b - a));
        }

        void tick(double x, double y) {
            double d = f * 0.28;
            line(x - d, y + d, x + d, y - d, "tk");
        }

        void vtext(double x, double y, String s) {
            sb.append("<text class=\"dt\" font-size=\"").append(n(f * 0.82)).append("\" text-anchor=\"middle\" transform=\"translate(")
              .append(n(x)).append(',').append(n(y)).append(") rotate(-90)\">").append(esc(s)).append("</text>\n");
        }

        // ---------- table ----------

        void table(double y0, double rowH) {
            String[] head = {"№", "Модуль", "Ш × В × Г", "Двери", "Ящики", "Полки", "Перег.", "Основание", "Прочее"};
            double[] frac = {0.045, 0.235, 0.15, 0.07, 0.10, 0.07, 0.07, 0.11, 0.15};
            double tw = svgW - 2 * m, fs = f * 0.82;
            double[] cx = new double[frac.length + 1];
            cx[0] = m;
            for (int i = 0; i < frac.length; i++) cx[i + 1] = cx[i] + tw * frac[i];

            sb.append("<rect class=\"th\" x=\"").append(n(m)).append("\" y=\"").append(n(y0)).append("\" width=\"").append(n(tw))
              .append("\" height=\"").append(n(rowH)).append("\"/>\n");
            for (int c = 0; c < head.length; c++) text(cx[c] + f * 0.4, y0 + rowH * 0.66, fs, "start", "thd", head[c]);

            for (int i = 0; i < mods.size(); i++) {
                ModuleSpec md = mods.get(i);
                double y = y0 + rowH * (i + 1);
                int sections = md.partitions() + 1;
                boolean inner = !(md.drawers() > 0 && md.doors() == 0);
                String[] row = {
                        String.valueOf(i + 1),
                        md.name(),
                        md.W() + " × " + md.H() + " × " + md.D(),
                        md.doors() > 0 ? String.valueOf(md.doors()) : "—",
                        md.drawers() > 0 ? md.drawers() + " × " + md.dh() : "—",
                        inner && md.shelves() > 0 ? String.valueOf(md.shelves() * sections) + (md.removable() ? "" : " н/с") : "—",
                        inner && md.partitions() > 0 ? String.valueOf(md.partitions()) : "—",
                        switch (md.base()) {
                            case "legs" -> "опоры " + md.legH();
                            case "plinth" -> "цоколь " + md.plinthH();
                            default -> "—";
                        },
                        extras(md)
                };
                for (int c = 0; c < row.length; c++) {
                    double colW = cx[c + 1] - cx[c] - f * 0.8;
                    text(cx[c] + f * 0.4, y + rowH * 0.66, fs, "start", c == 0 ? "tno" : "td", fit(row[c], colW, fs));
                }
            }
            double y1 = y0 + rowH * (mods.size() + 1);
            for (int r = 0; r <= mods.size() + 1; r++) line(m, y0 + rowH * r, m + tw, y0 + rowH * r, "tb");
            for (double c : cx) line(c, y0, c, y1, "tb");
        }

        String extras(ModuleSpec md) {
            List<String> e = new ArrayList<>();
            if (md.rod()) e.add("штанга");
            if (md.hang()) e.add("навесной");
            if ("rails".equals(md.top())) e.add("царги");
            if (!md.back()) e.add("без задн. стенки");
            if (md.doors() == 0 && md.drawers() == 0) e.add("открытый");
            return e.isEmpty() ? "—" : String.join(", ", e);
        }

        static String fit(String s, double width, double fs) {
            int max = (int) Math.max(3, width / (fs * 0.56));
            return s.length() <= max ? s : s.substring(0, max - 1) + "…";
        }

        // ---------- output helpers ----------

        void text(double x, double y, double size, String anchor, String cls, String s) {
            sb.append("<text class=\"").append(cls).append("\" x=\"").append(n(x)).append("\" y=\"").append(n(y))
              .append("\" font-size=\"").append(n(size)).append("\" text-anchor=\"").append(anchor).append("\">")
              .append(esc(s)).append("</text>\n");
        }

        void style() {
            sb.append("<style>\n")
              .append("text{font-family:Arial,'Helvetica Neue',Helvetica,sans-serif;fill:#1d1d1f}\n")
              .append(".tt{font-weight:700}.st{fill:#555}.lb{font-weight:700;fill:#333;letter-spacing:.04em}\n")
              .append(".bk{fill:#f6efe2}.nb{fill:#ffffff}\n")
              .append(".pn{fill:#dcc39a;stroke:#5a4630;stroke-width:").append(n(sw)).append("}\n")
              .append(".pl{fill:#c9ad80;stroke:#5a4630;stroke-width:").append(n(sw)).append("}\n")
              .append(".leg{fill:#4a4a4a}\n")
              .append(".bx{fill:#eee7da;stroke:#8c7a5e;stroke-width:").append(n(thin)).append(";stroke-dasharray:").append(n(f * 0.5)).append(' ').append(n(f * 0.3)).append("}\n")
              .append(".fr{fill:#eef2f6;stroke:#1f3d63;stroke-width:").append(n(sw)).append("}\n")
              .append(".op{fill:none;stroke:#1f3d63;stroke-opacity:.55;stroke-width:").append(n(thin)).append(";stroke-dasharray:").append(n(f * 0.6)).append(' ').append(n(f * 0.4)).append("}\n")
              .append(".hd{stroke:#1d1d1f;stroke-width:").append(n(sw * 2.6)).append(";stroke-linecap:round}\n")
              .append(".rod{stroke:#6b6b6b;stroke-width:").append(n(sw * 2.2)).append("}.rdot{fill:#6b6b6b}\n")
              .append(".no{fill:#ffffff;stroke:#b8322a;stroke-width:").append(n(thin * 1.6)).append("}.not{fill:#b8322a;font-weight:700}\n")
              .append(".flr{stroke:#1d1d1f;stroke-width:").append(n(sw * 1.6)).append("}\n")
              .append(".dim{stroke:#333;stroke-width:").append(n(thin)).append("}.tk{stroke:#333;stroke-width:").append(n(sw * 1.2)).append("}\n")
              .append(".ext{stroke:#8a8a8a;stroke-width:").append(n(thin * 0.8)).append("}.dt{fill:#1d1d1f}\n")
              .append(".th{fill:#eceef1}.tb{stroke:#555;stroke-width:").append(n(thin)).append("}.thd{font-weight:700}.tno{fill:#b8322a;font-weight:700}\n")
              .append("</style>\n");
        }

        static String n(double v) {
            if (Math.abs(v - Math.rint(v)) < 1e-6) return Long.toString(Math.round(v));
            return String.format(Locale.ROOT, "%.1f", v);
        }

        static String esc(String s) {
            StringBuilder o = new StringBuilder(s.length());
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '&' -> o.append("&amp;");
                    case '<' -> o.append("&lt;");
                    case '>' -> o.append("&gt;");
                    case '"' -> o.append("&quot;");
                    default -> o.append(c);
                }
            }
            return o.toString();
        }
    }
}
