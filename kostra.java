import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.plugin.PlugIn;
import ij.gui.*;

public class kostra implements PlugInFilter {
    public int setup(String arg, ImagePlus imp) {       
        return DOES_ALL;       
    }
private int tolerance = 0;

    public void run(ImageProcessor ip) {
        GenericDialog gd = new GenericDialog("Tolerance of whiteness");
        gd.addNumericField("Tolerance:", tolerance, 0);
        gd.showDialog();
        if (gd.wasOKed()) {
            // Get the entered values from the numeric fields
        tolerance = (int) gd.getNextNumber();
        }
        int width = ip.getWidth();
        int height = ip.getHeight();

        boolean horizontalPixelWhite = false;

        int lineStartX = 0;


        // Detekce a tvorba vodorovných přímek
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelValue = ip.getPixel(x, y);

                if (isWhitePixel(pixelValue, tolerance)) { // Funkce pro detekci bílého pixelu s tolerancí
                    if (!horizontalPixelWhite) {
                        horizontalPixelWhite = true;
                        lineStartX = x;
                    }
                } else {
                    if (horizontalPixelWhite) {
                        if (x - lineStartX >= 20) {
                            int newPixelValue = 0xFF0000; // Červená barva (R: 255, G: 0, B: 0)
                            for (int i = 0; i < width; i++) {
                                ip.putPixel(i, y, newPixelValue);
                            }
                        }
                        horizontalPixelWhite = false;
                    }
                }
            }
        }
        boolean verticalPixelWhite = false;
        int lineStartY = 0;
        int last_ver_line = 0;

// Detekce a tvorba svislých přímek
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixelValue = ip.getPixel(x, y);

                if (isWhitePixel(pixelValue, tolerance)) { // Funkce pro detekci bílého pixelu s tolerancí
                    if (!verticalPixelWhite) {
                        verticalPixelWhite = true;
                        lineStartY = y;
                    }
                } else { 
                        if (verticalPixelWhite) {
                            if (x - last_ver_line != 1 ){

                                if (y - lineStartY >= 20) {
                          

                                int newPixelValue = 0xFF0000; // Červená barva (R: 255, G: 0, B: 0)
                                    for (int i = 0; i < height; i++) {
                                    ip.putPixel(x, i, newPixelValue);  
                                    }
                                }
                                last_ver_line = x; 
                                verticalPixelWhite = false;
                            }       
                             
                        }
                    }
                }    
            }
        
        
        // Zobrazíme upravený obrázek
        ImagePlus resultImage = new ImagePlus("Extended Alternate Line Colors", ip);
        resultImage.show();
   } 
    
//public void run(String arg) {
        
//    }
    private boolean isWhitePixel(int pixelValue, int tolerance) {
        int red = (pixelValue >> 16) & 0xFF;
        int green = (pixelValue >> 8) & 0xFF;
        int blue = pixelValue & 0xFF;

        // Funkce pro detekci bílého pixelu s tolerancí
        return red >= 256 - tolerance && green >= 256 - tolerance && blue >= 256 - tolerance;
    }
}    
