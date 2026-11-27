import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * On-device screencap downscaler executed via {@code app_process /system/bin PenLabCap}.
 * Reads a PNG stream from stdin, resizes proportionally when wider than {@code maxWidth},
 * and writes compressed JPEG bytes to stdout.
 */
public class PenLabCap {
    public static void main(String[] args) throws Exception {
        int maxWidth = args.length > 0 ? Integer.parseInt(args[0]) : 1280;
        int quality = args.length > 1 ? Integer.parseInt(args[1]) : 78;

        if (maxWidth <= 0) {
            System.err.println("PenLabCap: maxWidth must be > 0");
            System.exit(1);
        }
        if (quality < 0 || quality > 100) {
            System.err.println("PenLabCap: quality must be in 0..100");
            System.exit(1);
        }

        Bitmap bmp = BitmapFactory.decodeStream(System.in);
        if (bmp == null) {
            System.err.println("PenLabCap: failed to decode bitmap from stdin");
            System.exit(1);
        }

        if (bmp.getWidth() > maxWidth) {
            int newHeight = Math.max(1, Math.round(bmp.getHeight() * (maxWidth / (float) bmp.getWidth())));
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, maxWidth, newHeight, true);
            if (scaled != bmp) {
                bmp.recycle();
            }
            bmp = scaled;
        }

        boolean ok = bmp.compress(Bitmap.CompressFormat.JPEG, quality, System.out);
        System.out.flush();
        bmp.recycle();

        if (!ok) {
            System.err.println("PenLabCap: failed to compress JPEG");
            System.exit(1);
        }
    }
}
