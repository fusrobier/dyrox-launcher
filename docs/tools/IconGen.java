import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws the Dyrox icon (dark rounded square, lime "D" with a glass sheen) and writes it as PNGs and a
 * multi-size Windows .ico. Regenerate with:
 *   java docs/tools/IconGen.java
 * from the repository root. Outputs are committed; the build does not run this.
 */
public class IconGen {
    public static void main(String[] args) throws IOException {
        Path launcherRes = Path.of("launcher/src/main/resources");
        Path packaging = Path.of("launcher/packaging");
        Path clientAssets = Path.of("client/src/main/resources/assets/dyrox");
        Files.createDirectories(launcherRes);
        Files.createDirectories(packaging);
        Files.createDirectories(clientAssets);

        ImageIO.write(draw(256), "png", launcherRes.resolve("dyrox-icon.png").toFile());
        ImageIO.write(draw(512), "png", packaging.resolve("dyrox.png").toFile());
        ImageIO.write(draw(128), "png", clientAssets.resolve("icon.png").toFile());

        List<byte[]> pngs = new ArrayList<>();
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        for (int size : sizes) pngs.add(png(draw(size)));
        Files.write(packaging.resolve("dyrox.ico"), ico(sizes, pngs));
        System.out.println("Icons written.");
    }

    static BufferedImage draw(int size) {
        // Draw large and scale down for clean edges at small sizes.
        int s = Math.max(size, 512);
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float pad = s * 0.04f;
        float box = s - 2 * pad;
        RoundRectangle2D.Float tile = new RoundRectangle2D.Float(pad, pad, box, box, box * 0.44f, box * 0.44f);
        g.setPaint(new GradientPaint(0, pad, new Color(0x252A33), 0, pad + box, new Color(0x0D0F12)));
        g.fill(tile);

        // Glass sheen on the upper half.
        g.setClip(tile);
        g.setPaint(new GradientPaint(0, pad, new Color(255, 255, 255, 46), 0, pad + box * 0.5f, new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, s, (int) (pad + box * 0.5f));
        g.setClip(null);
        g.setStroke(new BasicStroke(s * 0.008f));
        g.setColor(new Color(255, 255, 255, 60));
        g.draw(tile);

        // The "D": a vertical stem joined to a half circle, drawn as one thick stroke.
        float stroke = s * 0.115f;
        float left = s * 0.33f, top = s * 0.25f, height = s * 0.50f;
        float r = height / 2;
        Path2D.Float d = new Path2D.Float();
        d.moveTo(left, top);
        d.lineTo(left + height * 0.18f, top);
        d.append(new Arc2D.Float(left + height * 0.18f - r, top, 2 * r, 2 * r, 90, -180, Arc2D.OPEN), true);
        d.lineTo(left, top + height);
        d.closePath();
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(0, top, new Color(0xD9F99D), 0, top + height, new Color(0x84CC16)));
        g.draw(d);
        g.dispose();

        if (s == size) return img;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D o = out.createGraphics();
        o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        o.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // Halve repeatedly for smooth downscaling.
        BufferedImage current = img;
        int w = s;
        while (w / 2 >= size) {
            w /= 2;
            BufferedImage half = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
            Graphics2D h = half.createGraphics();
            h.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            h.drawImage(current, 0, 0, w, w, null);
            h.dispose();
            current = half;
        }
        o.drawImage(current, 0, 0, size, size, null);
        o.dispose();
        return out;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** ICO with PNG-compressed entries (supported since Windows Vista). */
    static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            b.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            b.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            b.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] p : pngs) b.put(p);
        return b.array();
    }
}
