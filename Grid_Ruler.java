/**
 * ImageJ Plugin: Grid_Ruler
 * 
 * Description: This plugin facilitates the automated recognition of images with grid (such as Bürker chamber), measuring real size of particles and counting of particles based on their size
 * 
 * Author: Stepan Helmer
 * 
 * Institution: Crop Research Institute, Prague
 * 
 * Contact: stepan.helmer@vurv.cz
 * 
 * Date: [06_17_2024]
 * 
 * License: [GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007]
 * 
 * Citation: []
 */

import ij.*;
import ij.measure.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.gui.*;
import ij.ImagePlus;
import ij.process.*;
import java.util.List;
import java.util.ArrayList; 
import java.io.File;
import java.util.Collections;
import ij.io.DirectoryChooser;
import ij.io.Opener;
import ij.io.FileSaver;
import java.io.FileWriter;
import java.io.IOException;


public class Grid_Ruler implements PlugInFilter {
    public int setup(String arg, ImagePlus imp) {
        return DOES_ALL+NO_IMAGE_REQUIRED; 
    }
    private List<Integer> rowColors = new ArrayList<Integer>(); //list of average colors of rows
    private List<Integer> colColors = new ArrayList<Integer>(); //list of average colors of cols
    private List<Integer> grid_linesX = new ArrayList<Integer>(); //list of detected lines X
    private List<Integer> grid_linesY = new ArrayList<Integer>(); //list of detected lines Y
    private List<Double> dist_Y = new ArrayList<Double>(); //distances between detected lines Y
    private List<Double> dist_X = new ArrayList<Double>(); //distances between detected lines X
    private List<Integer> square_linesX = new ArrayList<Integer>(); // detected lines X forming squares 
    private List<Integer> square_linesY = new ArrayList<Integer>(); // detected lines Y forming squares
    private ImageProcessor openedImageProcessor;    
    private int cropX = 0; // Size of cropped grid in X direction
    private int cropY = 0; // Size of cropped grid in Y direction
    private int longest_cropX = 0;
    private int longest_cropY = 0; 
    private int num_squares = 4; // Number of square in grid in one direction
    private int grid_size = 0; //size of grid in one direction in real units
    double size_of_par_min = 4; //minimum size of searched particles in real units
    double size_of_par_max = 9;//maximum size of searched particles in real units
    private String NameOfFile = null;
    boolean saveBinaryImage = false;
    boolean saveCroppedImage = false;
    private boolean saveCropped_orginal = true;
    String selectedFormat = "tiff";

    private StringBuilder logBuilder = new StringBuilder();
    private StringBuilder logparticle = new StringBuilder();


    public void run(ImageProcessor ip) {
        
        // Step 1: Setting image properties
        String[] imageFormats = {"tiff","jpg", "png", "gif", "bmp"};
        //Generic dialog for specifiing size parametres of grid and particles
        GenericDialog gd = new GenericDialog("Plugin options");
        gd.addNumericField("Size of grid:", grid_size, 0);
        gd.addNumericField("Number of squares:", num_squares, 0);
        gd.addNumericField("Min particle size:", size_of_par_min, 0);
        gd.addNumericField("Max particle size:", size_of_par_max, 0);
        gd.addCheckbox("Save original image", false);
        gd.addCheckbox("Save grayscale image", false);
        gd.addCheckbox("Save binary image", true);
        gd.addChoice("Format of image:", imageFormats, imageFormats[0]);
        gd.showDialog();
        if (gd.wasOKed()) {
            // Get the entered values from the numeric fields
            grid_size = (int) gd.getNextNumber();
            num_squares = (int) gd.getNextNumber();
            size_of_par_min = (double) gd.getNextNumber();
            size_of_par_max = (double) gd.getNextNumber();
            saveCropped_orginal = gd.getNextBoolean();
            saveCroppedImage = gd.getNextBoolean();
            saveBinaryImage = gd.getNextBoolean();
            selectedFormat = gd.getNextChoice();
            StringBuilder logBuilder = new StringBuilder();
            }

        logparticle.append("File" + "Counted particles");
        // Dialog window for choosing folder with pictures
        DirectoryChooser dc = new DirectoryChooser("Select Folder");
        String folderPath = dc.getDirectory();
        String Result_folder = folderPath + File.separator + "Results";
        File dir = new File(Result_folder);
        dir.mkdir();
        if (folderPath == null) {
            return;
            }

        // Opening of all images in selected folder
        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(selectedFormat)) {
                    NameOfFile = file.getName();
                    IJ.log("Processing file: " + file.getName());

                    // Opening of an image
                    Opener opener = new Opener();
                    ImagePlus imp = opener.openImage(file.getAbsolutePath());
                    if (imp != null) {

                        ImageProcessor original = imp.getProcessor();
                        // Step 2: Convertion into grayscale
                        ImageProcessor converted = original.convertToByte(true);
                        ImagePlus convertedIp = new ImagePlus("converted image", converted);
                        convertedIp.show();

                        int width = converted.getWidth();
                        int height = converted.getHeight();
                        int selectedX1 = 0;
                        int selectedX2 = 0;       
                        int selectedY1 = 0;
                        int selectedY2 = 0;
                        int[] histogram = new int[256];
                        ImageProcessor converted_cropped = null;
                        ImageProcessor original_cropped = null;  
			            ImageProcessor binary_cropped = null;
                        // Step 3: Calculation the average color of lines
                        for (int row = 0; row < height; row++) { // Go through all rows from the first to the last 
                            int sumCurrentRow = 0;       
                            // Count the avarage 8-bit color of row
                            for (int col = 0; col < width; col++) {
                                sumCurrentRow += converted.getPixel(col, row);
                            }
                            int avgCurrentRow = sumCurrentRow / width;
                            rowColors.add(avgCurrentRow);
                        }

                        for (int col = 0; col < width; col++) {      //  Go through all column from the left to the right
                            int sumCurrentCol = 0;
                        // Count the avarage 8-bit color of column
                            for (int row = 0; row < height - 1; row++) {
                                sumCurrentCol += converted.getPixel(col, row);
                            }
                        int avgCurrentCol = sumCurrentCol / height;
                        colColors.add(avgCurrentCol);
                        }          
        
                        for (int z = 10 ; z > 1 ; z--) {
                            //Step 4: Grid lines detection
                            for (int row = 0; row < height - 1; row++) {
                                if (rowColors.get(row)>(rowColors.get(row + 1)+z)){ // Lines that are higher by defined value compared to previus are detected
                                grid_linesY.add(row);
                                }
                            }
                            for (int col = 0; col < width - 1; col++) {
                                if(colColors.get(col)>(colColors.get(col + 1)+z)){ // Lines that are higher by defined value compared to previus are detected
                                grid_linesX.add(col);
                                }
                            }
                            //Step 5: Recognition of lines making squares
                            if (!grid_linesY.isEmpty() && !grid_linesX.isEmpty()){
                                for (int i = 0; i < grid_linesY.size() - 1 ; i++) {
                                    for (int j = 0; j < grid_linesX.size() -1 ; j++) {
                                        double difX = (double) grid_linesX.get(j+1)-(double) grid_linesX.get(j);
                                        double difY = (double) grid_linesY.get(i+1)-(double) grid_linesY.get(i);
                                        if ((difX/difY) > 0.8 && (difX/difY)< 1.2){
                                            dist_X.add(difX);
                                            dist_Y.add(difY);
                                        }
                                    }
                                }
                            }   
                            //Step 6:   Filtration of lines
                            if (!grid_linesY.isEmpty() && !dist_Y.isEmpty()){
                                for (int i = 0; i < grid_linesY.size() - 1 ; i++) {            
                                    double maxdifY = Collections.max(dist_Y);
                                    double difY = (double) grid_linesY.get(i+1)-(double) grid_linesY.get(i);
                                    if ( (difY/maxdifY) > 0.75 ){
                                        square_linesY.add(grid_linesY.get(i));
                                        square_linesY.add(grid_linesY.get(i+1));
                                    }
                                }  
                            }                
                            if (!grid_linesX.isEmpty() && !dist_X.isEmpty()){
                                for (int j = 0; j < grid_linesX.size() -1 ; j++) {
                                    double maxdifX = Collections.max(dist_X);
                                    double difX = (double) grid_linesX.get(j+1)-(double) grid_linesX.get(j);
                                    if ((difX/maxdifX) > 0.75){
                                        square_linesX.add(grid_linesX.get(j));
                                        square_linesX.add(grid_linesX.get(j+1));
                                    }
                                }
                            }  
            
                            // Step 7: Recognition of grid
                            if (square_linesX.size() >= num_squares*2 && square_linesY.size() >= num_squares*2 ){
                                selectedX1 = square_linesX.get(0);
                                selectedX2 = square_linesX.get((num_squares*2) - 1);
                                selectedY1 = square_linesY.get(0);
                                selectedY2 = square_linesY.get((num_squares*2) - 1);

                            //Step 8: Grid Selection
                                int cropX = selectedX2 - selectedX1;
                                int cropY = selectedY2 - selectedY1;   
                                double podil = (double) cropX / cropY;

                                if (podil > 0.9 && podil < 1.2 && longest_cropX <= cropX && longest_cropY <= cropY){
                            // Creation of cropped image

                            // Step 9: Masking of grid lines
                                    int newPixelValue = 255;
                                    converted_cropped = converted.duplicate();
                                    for (int row :grid_linesY) {
					                    for (int col = 0; col < width; col++) {
						                    int rowrow = converted_cropped.getPixel(col, row);
						                    int nextrow = converted_cropped.getPixel(col, row+1);
                                            if (rowrow <= (nextrow + z)){ // Lines that are higher by defined value compared to previus are detected
                                                converted_cropped.putPixel(col,row, newPixelValue);
                                            }
                                        }
                                    }
                                    for (int col:grid_linesX) {
                                        for (int row = 0; row < height; row++) {
						                    int colcol = converted_cropped.getPixel(col, row);
						                    int nextcol = converted_cropped.getPixel(col+1, row);
                                            if (colcol <= (nextcol + z)) { // Lines that are higher by defined value compared to previus are detected
                                                converted_cropped.putPixel(col,row, newPixelValue);
                                            }
                                        }
                                    }
                                    original_cropped = original.duplicate();
                                    original_cropped.setRoi(selectedX1, selectedY1, cropX, cropY);
                                    //original_cropped = original_cropped.crop();

                                    converted_cropped.setRoi(selectedX1, selectedY1, cropX, cropY);
                                    converted_cropped = converted_cropped.crop();

                //croppedImage.close();
                                    int longest_cropX = cropX;
                                    int longest_cropY = cropY;
                                }       

                            }
                            grid_linesX.clear();
                            grid_linesY.clear();
                            dist_Y.clear();
                            dist_X.clear();
                            square_linesY.clear();
                            square_linesX.clear();
                        }
                        if (converted_cropped != null) {            
                            ImagePlus croppedImage = new ImagePlus("Cropped Image", converted_cropped);
                            croppedImage.show();  
                            ImagePlus cropped_original = new ImagePlus("Cropped Image", original_cropped);
                            cropped_original.show();  

                            //Step 10: Size calibration
                            longest_cropX = converted_cropped.getWidth();
                            longest_cropY = converted_cropped.getHeight();
                            int longest_crop = Math.min(longest_cropX, longest_cropY);
                            double unit_size = (double) grid_size / (double) longest_crop;
                            double sizer_coef = unit_size * unit_size;
					        binary_cropped = converted_cropped.duplicate();
                            ImagePlus BinaryIp = new ImagePlus("converted image", binary_cropped);
                            // Nastavení kalibrace
                            Calibration cal = new Calibration();
                            cal.setUnit("nm");
                            cal.pixelWidth = unit_size; // Width of pixel in own units
                            cal.pixelHeight = unit_size; // Height of pixel in own units (nm)
                            BinaryIp.setCalibration(cal);
                            cropped_original.setCalibration(cal);

                            //Step 11 : Binarization/tresholding                                            
                            BinaryIp.show();
                            IJ.setAutoThreshold(BinaryIp, "MaxEntropy");
                            IJ.run(BinaryIp, "Convert to Mask", "");
        
                            //Step 12: Objects pre-processing
                            IJ.run(BinaryIp, "Dilate", "");
					        IJ.run(BinaryIp, "Fill Holes", "");
                            IJ.run(BinaryIp, "Watershed", "");                                      
                            IJ.run(BinaryIp, "Erode", "");
                            if (saveBinaryImage) {
                                FileSaver fileSaver = new FileSaver(BinaryIp);
                                fileSaver.saveAsTiff(Result_folder + File.separator + "binary_" + NameOfFile);
                            }
                            if (saveCroppedImage) {
                                FileSaver fileSaver = new FileSaver(croppedImage);
                                fileSaver.saveAsTiff(Result_folder + File.separator + "grayscale_" + NameOfFile);
                            }
                            if (saveCropped_orginal) {
                                FileSaver fileSaver = new FileSaver(cropped_original);
                                fileSaver.saveAsTiff(Result_folder + File.separator + "cropped_original_" + NameOfFile);
                            }
                            // Step 13: Particle analyses
                            // Minimum and maximum particle size
                            double minSize = (0.5 * size_of_par_min) * (0.5 * size_of_par_min)*3/sizer_coef; // Specify the minimum particle size according to your needs
                            double maxSize = (0.5 * size_of_par_max) * (0.5 * size_of_par_max)*3/sizer_coef; // Specify the maximum particle size according to your needs
                                        
                            // Running of particle analysis
                            ParticleAnalyzer analyzer = new ParticleAnalyzer(
                            ParticleAnalyzer.CLEAR_WORKSHEET | ParticleAnalyzer.ADD_TO_MANAGER,
                            Measurements.AREA | Measurements.CENTROID | Measurements.INTEGRATED_DENSITY,
                            null, minSize, maxSize, 0.2, 1.0);
                            analyzer.analyze(BinaryIp);

                            // Get RoiManager instance
                            RoiManager roiManager = RoiManager.getInstance();
                            if (roiManager == null) {
                                roiManager = new RoiManager();
                            }

                                // Get the list of ROIs
                            Roi[] rois = roiManager.getRoisAsArray();
                            int count = roiManager.getCount();
                            logparticle.append(NameOfFile + "\t" + count + "\n");
                            String Particle_data = logparticle.toString();
                            String Particle_path =  Result_folder + File.separator + "particle-count.csv";
                            try {
                                FileWriter writer = new FileWriter(Particle_path);
                                writer.write(Particle_data);
                                writer.close();
                            } catch (IOException e) {
                            }
                                        // Print information about detected particles
                            for (int i = 0; i < rois.length; i++) {
                                Roi roi = rois[i];
                                double area = roi.getStatistics().area;
                                double x = roi.getXBase();
                                double y = roi.getYBase();
            
                                logBuilder.append(NameOfFile + " Particle_" + (i + 1) + " Area_(pixels)_= " + area + " Centroid= " + x + " " + y + "\n");
                                String logData = logBuilder.toString();
                                String savePath = Result_folder + File.separator + "Particle-parametres.csv"; // Change to path you want
              
                                //List<String> roiList = roiManager.getList();
                                String txtPath = folderPath + File.separator + "log.zip";
                                //IJ.saveString(roiList, txtPath);
                                roiManager.runCommand("Save", txtPath);
                                try {
                                FileWriter writer = new FileWriter(savePath);
                                writer.write(logData);

                                writer.close();
            
                                } catch (IOException e) {
                                }
                            }
                        }
                    }
                    cropX = 0;
                    cropY = 0; 
                    longest_cropX = 0;
                    longest_cropY = 0;
                    rowColors.clear();
                    colColors.clear();
                }
            }
        }   
    }                     
}
