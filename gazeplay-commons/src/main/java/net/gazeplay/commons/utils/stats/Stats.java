package net.gazeplay.commons.utils.stats;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Polygon;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.gazeplay.commons.configuration.ActiveConfigurationContext;
import net.gazeplay.commons.configuration.Configuration;
import net.gazeplay.commons.gaze.GazeMotionListener;
import net.gazeplay.commons.gaze.devicemanager.GazeEvent;
import net.gazeplay.commons.utils.FixationPoint;
import net.gazeplay.commons.utils.FixationSequence;
import net.gazeplay.commons.utils.HeatMap;
import net.gazeplay.commons.utils.games.DateUtils;
import net.gazeplay.commons.utils.games.GazePlayDirectories;
import org.monte.media.Format;
import org.monte.media.FormatKeys;
import org.monte.media.VideoFormatKeys;
import org.monte.media.gui.Worker;
import org.monte.media.math.Rational;
import org.monte.screenrecorder.ScreenRecorder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.*;

import static java.lang.Math.pow;


/**
 * Created by schwab on 16/08/2017.
 */
@Slf4j
@ToString
public class Stats implements GazeMotionListener {

    private static final int trail = 10;
    private static final int fixationTrail = 50;
    private final double heatMapPixelSize;
    private final Scene gameContextScene;
    protected String gameName;

    long startTime;
    int sceneCounter = 0;
    private EventHandler<MouseEvent> recordMouseMovements;
    private EventHandler<GazeEvent> recordGazeMovements;
    private LifeCycle lifeCycle = new LifeCycle();
    private RoundsDurationReport roundsDurationReport = new RoundsDurationReport();
    private int counter = 0;
    private final List<CoordinatesTracker> movementHistory = new ArrayList<>();
    private long previousTime = 0;
    private int previousX = 0;
    private int previousY = 0;
    private File movieFolder;
    private boolean convexHULL = true;
    private ScreenRecorder screenRecorder;
    private ArrayList<TargetAOI> targetAOIList = null;
    private double[][] heatMap;

    @Getter
    public int nbGoalsReached = 0;

    @Getter
    protected int nbGoalsToReach = 0;

    @Setter
    private long accidentalShotPreventionPeriod = 0;

    @Getter
    private int nbUnCountedGoalsReached;

    @Getter
    @Setter
    private long currentGazeTime;

    @Getter
    @Setter
    private long lastGazeTime;

    @Getter
    private LinkedList<FixationPoint> fixationSequence;

    @Getter
    private SavedStatsInfo savedStatsInfo;

    @Getter
    private WritableImage gameScreenShot;

    private String directoryOfVideo;

    private String nameOfVideo;

    private Long currentRoundStartTime;


    //peremeters for AOI
    private int movementHistoryidx = 0;
    private final List<AreaOfInterestProps> allAOIList = new ArrayList<>();
    private List<CoordinatesTracker> areaOfInterestList = new ArrayList<>();
    @Getter
    private final List<List> allAOIListTemp = new ArrayList<>();
    @Getter
    private final List<Polygon> allAOIListPolygon = new ArrayList<>();
    @Getter
    private final List<Double[]> allAOIListPolygonPt = new ArrayList<>();
    private double highestFixationTime = 0;
    private final Configuration config = ActiveConfigurationContext.getInstance();
    private int colorIterator;
    private final javafx.scene.paint.Color[] colors = new javafx.scene.paint.Color[]{
        javafx.scene.paint.Color.PURPLE,
        javafx.scene.paint.Color.WHITE,
        javafx.scene.paint.Color.PINK,
        javafx.scene.paint.Color.ORANGE,
        javafx.scene.paint.Color.BLUE,
        javafx.scene.paint.Color.RED,
        javafx.scene.paint.Color.CHOCOLATE
    };


    public Stats(final Scene gameContextScene) {
        this(gameContextScene, null);
    }

    public Stats(final Scene gameContextScene, final String gameName) {
        this.gameContextScene = gameContextScene;
        this.gameName = gameName;

        heatMapPixelSize = computeHeatMapPixelSize(gameContextScene);
    }

    static double[][] instantiateHeatMapData(final Scene gameContextScene, final double heatMapPixelSize) {
        final int heatMapWidth = (int) (gameContextScene.getHeight() / heatMapPixelSize);
        final int heatMapHeight = (int) (gameContextScene.getWidth() / heatMapPixelSize);
        log.info("heatMapWidth = {}, heatMapHeight = {}", heatMapWidth, heatMapHeight);
        return new double[heatMapWidth][heatMapHeight];
    }

    public ArrayList<TargetAOI> getTargetAOIList() {
        return this.targetAOIList;
    }

    public void setTargetAOIList(final ArrayList<TargetAOI> targetAOIList) {
        this.targetAOIList = targetAOIList;
        for (int i = 0; i < targetAOIList.size() - 1; i++) {
            final long duration = targetAOIList.get(i).getTimeEnded() - targetAOIList.get(i).getTimeStarted();
            this.targetAOIList.get(i).setDuration(duration);
        }
        if (targetAOIList.size() >= 1) {
            targetAOIList.get(targetAOIList.size() - 1).setDuration(0);
        }
    }

    public void notifyNewRoundReady() {
        currentRoundStartTime = System.currentTimeMillis();
        takeScreenShot();
    }

    public void notifyNextRound() {
        final long currentRoundEndTime = System.currentTimeMillis();
        final long currentRoundDuration = currentRoundEndTime - this.currentRoundStartTime;
        this.roundsDurationReport.addRoundDuration(currentRoundDuration);
        currentRoundStartTime = currentRoundEndTime;
    }

    public void startVideoRecording() {
        directoryOfVideo = getGameStatsOfTheDayDirectory().toString();
        this.movieFolder = new File(directoryOfVideo);
        final float quality = 1.0F;
        final byte bitDepth = 24;

        final String mimeType;
        final String videoFormatName;
        final String compressorName;

        mimeType = "video/avi";
        videoFormatName = "tscc";
        compressorName = "Techsmith Screen Capture";

        System.setProperty("java.awt.headless", "false");
        final GraphicsConfiguration cfg = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
            .getDefaultConfiguration();
        final Rectangle areaRect;
        final Dimension outputDimension;
        areaRect = cfg.getBounds();

        outputDimension = areaRect.getSize();
        final byte screenRate;
        screenRate = 30;

        Format fileFormat = new Format(
            VideoFormatKeys.MediaTypeKey,
            FormatKeys.MediaType.FILE,
            VideoFormatKeys.MimeTypeKey,
            mimeType
        );

        Format screenFormat =  new Format(
            VideoFormatKeys.MediaTypeKey,
            FormatKeys.MediaType.VIDEO,
            VideoFormatKeys.EncodingKey,
            videoFormatName,
            VideoFormatKeys.CompressorNameKey,
            compressorName,
            VideoFormatKeys.WidthKey,
            outputDimension.width,
            VideoFormatKeys.HeightKey,
            outputDimension.height,
            VideoFormatKeys.DepthKey,
            (int) bitDepth,
            VideoFormatKeys.FrameRateKey,
            Rational.valueOf(screenRate),
            VideoFormatKeys.QualityKey,
            quality,
            VideoFormatKeys.KeyFrameIntervalKey,
            screenRate * 60
        );

        try {
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss");
            nameOfVideo = this.movieFolder + "/ScreenRecording " + dateFormat.format(new Date());
            this.screenRecorder = new ScreenRecorder(cfg, areaRect, fileFormat, screenFormat, null, null, this.movieFolder);
            this.screenRecorder.start();
        } catch (IOException | AWTException e) {
            e.printStackTrace();
        }
        this.screenRecorder.setAudioMixer(null);
    }

    public void endVideoRecording() {
        final ScreenRecorder recorder = this.screenRecorder;
        (new Worker<>() {
            @Override
            protected Object construct() throws Exception {
                recorder.stop();
                return null;
            }

            @Override
            protected void finished() {
            }
        }).start();
    }

    private void generateAOIList(final int index, final double startTime) {
        final double x1 = movementHistory.get(index).getXValue();
        final double y1 = movementHistory.get(index).getYValue();
        final double x2 = movementHistory.get(index - 1).getXValue();
        final double y2 = movementHistory.get(index - 1).getYValue();
        final double eDistance = Math.sqrt(pow(x2 - x1, 2) + pow(y2 - y1, 2));
        if (eDistance < 120 && movementHistory.get(index).getIntervalTime() > 10) {
            if (index == 1) {
                areaOfInterestList.add(movementHistory.get(0));
            }
            areaOfInterestList.add(movementHistory.get(index));
        } else if (!areaOfInterestList.isEmpty()) {
            if (areaOfInterestList.size() > 2) {

                allAOIListTemp.add(new ArrayList<>(areaOfInterestList));

                final Point2D[] points = new Point2D[areaOfInterestList.size()];

                for (int i = 0; i < areaOfInterestList.size(); i++) {
                    CoordinatesTracker coordinate = areaOfInterestList.get(i);
                    points[i] = new Point2D(coordinate.getXValue(), coordinate.getYValue());
                }

                final Double[] polygonPoints;
                if (config.getConvexHullDisabledProperty().getValue()) {
                    polygonPoints = calculateConvexHull(points);
                } else {
                    polygonPoints = calculateRectangle(points);
                }

                final Polygon areaOfInterest = new Polygon();
                areaOfInterest.getPoints().addAll(polygonPoints);
                allAOIListPolygonPt.add(polygonPoints);

                colorIterator = index % 7;
                areaOfInterest.setStroke(colors[colorIterator]);
                allAOIListPolygon.add(areaOfInterest);
            }else if(eDistance > 200){
                allAOIListTemp.add(new ArrayList<>(areaOfInterestList));
                final float radius = 15;
                final Point2D[] points = new Point2D[8];

                for (int i = 0; i < 8; i++) {
                    CoordinatesTracker coordinate = areaOfInterestList.get(0);
                    points[i] = new Point2D(coordinate.getXValue()+pow(-1,i)*radius, coordinate.getYValue()+pow(-1,i)*radius);
                }

                final Double[] polygonPoints;
                if (config.getConvexHullDisabledProperty().getValue()) {
                    polygonPoints = calculateConvexHull(points);
                } else {
                    polygonPoints = calculateRectangle(points);
                }

                final Polygon areaOfInterest = new Polygon();
                areaOfInterest.getPoints().addAll(polygonPoints);
                allAOIListPolygonPt.add(polygonPoints);

                colorIterator = index % 7;
                areaOfInterest.setStroke(colors[colorIterator]);
                allAOIListPolygon.add(areaOfInterest);
            }
            areaOfInterestList = new ArrayList<>();
            areaOfInterestList.add(movementHistory.get(index));
        }
    }

    static Double[] calculateRectangle(final Point2D[] point2D) {
        double leftPoint = point2D[0].getX();
        double rightPoint = point2D[0].getX();
        double topPoint = point2D[0].getY();
        double bottomPoint = point2D[0].getY();

        for (int i = 1; i < point2D.length; i++) {
            if (point2D[i].getX() < leftPoint) {
                leftPoint = point2D[i].getX();
            }
            if (point2D[i].getX() > rightPoint) {
                rightPoint = point2D[i].getX();
            }
            if (point2D[i].getY() > topPoint) {
                topPoint = point2D[i].getY();
            }
            if (point2D[i].getY() < bottomPoint) {
                bottomPoint = point2D[i].getY();
            }
        }

        final Double[] squarePoints = new Double[8];
        final int bias = 15;

        squarePoints[0] = leftPoint - bias;
        squarePoints[1] = topPoint + bias;
        squarePoints[2] = rightPoint + bias;
        squarePoints[3] = topPoint + bias;
        squarePoints[4] = rightPoint + bias;
        squarePoints[5] = bottomPoint - bias;
        squarePoints[6] = leftPoint - bias;
        squarePoints[7] = bottomPoint - bias;
        return squarePoints;
    }

    static Double[] calculateConvexHull(final Point2D[] points) {
        final int numberOfPoints = points.length;
        final ArrayList<Double> convexHullPoints = new ArrayList<>();
        final Vector<Point2D> hull = new Vector<>();

        // Finding the index of the lowest X value, or left-most point, in all points.
        int lowestValueIndex = 0;
        for (int i = 1; i < numberOfPoints; i++) {
            if (points[i].getX() < points[lowestValueIndex].getX()) {
                lowestValueIndex = i;
            }
        }

        int point = lowestValueIndex, q;
        do {
            hull.add(points[point]);
            q = (point + 1) % numberOfPoints;
            for (int i = 0; i < numberOfPoints; i++) {
                if (orientation(points[point], points[i], points[q]) < 0) { // Checking if the points are convex.
                    q = i;
                }
            }
            point = q;
        } while (point != lowestValueIndex);

        for (final Point2D temp : hull) {
            convexHullPoints.add(temp.getX());
            convexHullPoints.add(temp.getY());
        }

        Double[] hullPointsArray = new Double[convexHullPoints.size()];
        convexHullPoints.toArray(hullPointsArray);

        return hullPointsArray;
    }

    static int orientation(final Point2D p1, final Point2D p2, final Point2D p3) {

        final int val = (int) ((p2.getY() - p1.getY()) * (p3.getX() - p2.getX())
            - (p2.getX() - p1.getX()) * (p3.getY() - p2.getY()));

        if (val == 0) {
            return 0;
        }

        return (val > 0) ? 1 : -1;
    }

    public void start() {
        final Configuration config = ActiveConfigurationContext.getInstance();
        if (config.isVideoRecordingEnabled()) {
            startVideoRecording();
        }
        lifeCycle.start(() -> {
            if (!config.isHeatMapDisabled()) {
                heatMap = instantiateHeatMapData(gameContextScene, heatMapPixelSize);
            }
            if (!config.isFixationSequenceDisabled()) {
                fixationSequence = new LinkedList<>();
            }
            startTime = System.currentTimeMillis();

            recordGazeMovements = e -> {
                final int getX = (int) e.getX();
                final int getY = (int) e.getY();
                if (!config.isHeatMapDisabled()) {
                    incrementHeatMap(getX, getY);
                }
                if (!config.isFixationSequenceDisabled()) {
                    incrementFixationSequence(getX, getY);
                }
                if (config.getAreaOfInterestDisabledProperty().getValue()) {
                    if (getX != previousX || getY != previousY) {
                        final long timeToFixation = System.currentTimeMillis() - startTime;
                        previousX = getX;
                        previousY = getY;
                        final long timeInterval = (timeToFixation - previousTime);
                        movementHistory
                            .add(new CoordinatesTracker(getX, getY, timeInterval, System.currentTimeMillis()));
                        movementHistoryidx++;
                        log.info("movementHistory length {}",movementHistory.size());
                        if (movementHistoryidx > 1) {
                            generateAOIList(movementHistoryidx - 1, startTime);
                        }
                        previousTime = timeToFixation;
                    }
                }
            };

            recordMouseMovements = e -> {
                final int getX = (int) e.getX();
                final int getY = (int) e.getY();
                if (!config.isHeatMapDisabled()) {
                    incrementHeatMap(getX, getY);
                }
                if (!config.isFixationSequenceDisabled()) {
                    incrementFixationSequence(getX, getY);
                }
                if (config.getAreaOfInterestDisabledProperty().getValue()) {
                    if (getX != previousX || getY != previousY && counter == 2) {
                        final long timeElapsedMillis = System.currentTimeMillis() - startTime;
                        previousX = getX;
                        previousY = getY;
                        final long timeInterval = (timeElapsedMillis - previousTime);
                        movementHistory
                            .add(new CoordinatesTracker(getX, getY, timeInterval, System.currentTimeMillis()));
                        movementHistoryidx++;
                        if (movementHistoryidx > 1) {
                            generateAOIList(movementHistoryidx - 1, startTime);
                        }
                        previousTime = timeElapsedMillis;
                        counter = 0;
                    }
                    counter++;
                }
            };

            gameContextScene.addEventFilter(GazeEvent.ANY, recordGazeMovements);
            gameContextScene.addEventFilter(MouseEvent.ANY, recordMouseMovements);

        });
        currentRoundStartTime = lifeCycle.getStartTime();
    }

    public List<CoordinatesTracker> getMovementHistoryWithTime() {
        return this.movementHistory;
    }

    public void reset() {
        nbGoalsReached = 0;
        nbGoalsToReach = 0;
        accidentalShotPreventionPeriod = 0;

        roundsDurationReport = new RoundsDurationReport();
        lifeCycle = new LifeCycle();
        start();
    }

    public void stop() {
        final Configuration config = ActiveConfigurationContext.getInstance();
        if (config.isVideoRecordingEnabled()) {
            endVideoRecording();
        }
        lifeCycle.stop(() -> {
            if (recordGazeMovements != null) {
                gameContextScene.removeEventFilter(GazeEvent.ANY, recordGazeMovements);
            }
            if (recordMouseMovements != null) {
                gameContextScene.removeEventFilter(MouseEvent.ANY, recordMouseMovements);
            }
        });
    }

    @Override
    public void gazeMoved(final javafx.geometry.Point2D position) {
        final int positionX = (int) position.getX();
        final int positionY = (int) position.getY();
        incrementHeatMap(positionX, positionY);
        incrementFixationSequence(positionX, positionY);
    }

    static void saveImageAsPng(final BufferedImage bufferedImage, final File outputFile) {
        try {
            ImageIO.write(bufferedImage, "png", outputFile);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SavedStatsInfo saveStats() throws IOException {
        final Configuration config = ActiveConfigurationContext.getInstance();

        final File todayDirectory = getGameStatsOfTheDayDirectory();
        final String now = DateUtils.dateTimeNow();
        final String heatmapFilePrefix = now + "-heatmap";
        final String gazeMetricsFilePrefix = now + "-metrics";
        final String screenShotFilePrefix = now + "-screenshot";
        final String colorBandsFilePrefix = now + "-colorBands";

        final File gazeMetricsFile = new File(todayDirectory, gazeMetricsFilePrefix + ".png");
        final File heatMapCsvFile = new File(todayDirectory, heatmapFilePrefix + ".csv");
        final File screenShotFile = new File(todayDirectory, screenShotFilePrefix + ".png");
        final File colorBandsFile = new File(todayDirectory, colorBandsFilePrefix + "png");

        final BufferedImage screenshotImage = SwingFXUtils.fromFXImage(gameScreenShot, null);
        saveImageAsPng(screenshotImage, screenShotFile);

        final BufferedImage bImage = new BufferedImage(
            screenshotImage.getWidth() + (heatMap != null ? screenshotImage.getWidth() / 20 + 10 : 0),
            screenshotImage.getHeight(), screenshotImage.getType());

        final Graphics g = bImage.getGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, bImage.getWidth(), bImage.getHeight());
        g.drawImage(screenshotImage, 0, 0, null);

        final SavedStatsInfo savedStatsInfo = new SavedStatsInfo(heatMapCsvFile, gazeMetricsFile, screenShotFile,
            colorBandsFile);

        this.savedStatsInfo = savedStatsInfo;
        if (this.heatMap != null) {
            final HeatMap hm = new HeatMap(heatMap, config.getHeatMapOpacity(), config.getHeatMapColors());
            BufferedImage heatmapImage = SwingFXUtils.fromFXImage(hm.getImage(), null);
            final Kernel kernel = new Kernel(3, 3,
                new float[]{1 / 16f, 1 / 8f, 1 / 16f, 1 / 8f, 1 / 4f, 1 / 8f, 1 / 16f, 1 / 8f, 1 / 16f});
            final BufferedImageOp op = new ConvolveOp(kernel);
            heatmapImage = op.filter(heatmapImage, null);
            g.drawImage(heatmapImage, 0, 0, screenshotImage.getWidth(), screenshotImage.getHeight(), null);

            final BufferedImage key = SwingFXUtils.fromFXImage(hm.getColorKey(bImage.getWidth() / 20, bImage.getHeight() / 2),
                null);
            g.drawImage(key, bImage.getWidth() - key.getWidth(), (bImage.getHeight() - key.getHeight()) / 2, null);

            saveHeatMapAsCsv(heatMapCsvFile);
        }

        if (this.fixationSequence != null) {
            // set the gazeDuration of the last Fixation Point
            fixationSequence.get(fixationSequence.size() - 1)
                .setGazeDuration(fixationSequence.get(fixationSequence.size() - 1).getTimeGaze()
                    - fixationSequence.get(fixationSequence.size() - 2).getTimeGaze());
            final FixationSequence scanpath = new FixationSequence((int) gameContextScene.getWidth(),
                (int) gameContextScene.getHeight(), fixationSequence);
            fixationSequence = scanpath.getSequence();
            final BufferedImage seqImage = SwingFXUtils.fromFXImage(scanpath.getImage(), null);
            g.drawImage(seqImage, 0, 0, screenshotImage.getWidth(), screenshotImage.getHeight(), null);
        }

        saveImageAsPng(bImage, gazeMetricsFile);

        savedStatsInfo.notifyFilesReady();
        return savedStatsInfo;
    }

    public long computeRoundsDurationAverageDuration() {
        return roundsDurationReport.computeAverageLength();
    }

    public long getStartTime() {
        return this.startTime;
    }

    public long computeRoundsDurationMedianDuration() {
        return roundsDurationReport.computeMedianDuration();
    }

    public long getRoundsTotalAdditiveDuration() {
        return roundsDurationReport.getTotalAdditiveDuration();
    }

    public long computeTotalElapsedDuration() {
        return lifeCycle.computeTotalElapsedDuration();
    }

    public double computeRoundsDurationVariance() {
        return roundsDurationReport.computeVariance();
    }

    public double computeRoundsDurationStandardDeviation() {
        return roundsDurationReport.computeSD();
    }

    public void incrementNumberOfGoalsToReach() {
        nbGoalsToReach++;
        currentRoundStartTime = System.currentTimeMillis();
        log.debug("The number of goals is " + nbGoalsToReach + "and the number shots is " + nbGoalsReached);
    }

    public void incrementNumberOfGoalsToReach(int i) {
        nbGoalsToReach += i;
        currentRoundStartTime = System.currentTimeMillis();
        log.debug("The number of goals is " + nbGoalsToReach + "and the number shots is " + nbGoalsReached);
    }

    public void incrementNumberOfGoalsReached() {
        final long currentRoundEndTime = System.currentTimeMillis();
        final long currentRoundDuration = currentRoundEndTime - currentRoundStartTime;
        if (currentRoundDuration < accidentalShotPreventionPeriod) {
            nbUnCountedGoalsReached++;
        } else {
            nbGoalsReached++;
            this.roundsDurationReport.addRoundDuration(currentRoundDuration);
        }
        currentRoundStartTime = currentRoundEndTime;
        log.debug("The number of goals is " + nbGoalsToReach + "and the number shots is " + nbGoalsReached);
    }

    public void addRoundDuration() {
        this.roundsDurationReport.addRoundDuration(System.currentTimeMillis() - currentRoundStartTime);
    }

    public int getShotRatio() {
        if (this.nbGoalsToReach == this.nbGoalsReached || this.nbGoalsToReach == 0) {
            return 100;
        } else {
            return (int) ((float) this.nbGoalsReached / (float) this.nbGoalsToReach * 100.0);
        }
    }

    public List<Long> getSortedDurationsBetweenGoals() {
        return this.roundsDurationReport.getSortedDurationsBetweenGoals();
    }

    public List<Long> getOriginalDurationsBetweenGoals() {
        return this.roundsDurationReport.getOriginalDurationsBetweenGoals();
    }

    public String getDirectoryOfVideo() {
        return nameOfVideo;
    }

    protected File createInfoStatsFile() {
        final File outputDirectory = getGameStatsOfTheDayDirectory();

        final String fileName = DateUtils.dateTimeNow() + "-info-game.csv";
        return new File(outputDirectory, fileName);
    }

    protected File getGameStatsOfTheDayDirectory() {
        final File statsDirectory = GazePlayDirectories.getUserStatsFolder(ActiveConfigurationContext.getInstance().getUserName());
        final File gameDirectory = new File(statsDirectory, gameName);
        final File todayDirectory = new File(gameDirectory, DateUtils.today());
        final boolean outputDirectoryCreated = todayDirectory.mkdirs();
        log.info("outputDirectoryCreated = {}", outputDirectoryCreated);

        return todayDirectory;
    }

    protected void printLengthBetweenGoalsToString(final PrintWriter out) {
        this.roundsDurationReport.printLengthBetweenGoalsToString(out);
    }

    private void saveHeatMapAsCsv(final File file) throws IOException {
        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8)) {
            for (final double[] doubles : heatMap) {
                for (int j = 0; j < heatMap[0].length - 1; j++) {
                    out.print((int) doubles[j]);
                    out.print(", ");
                }
                out.print((int) doubles[doubles.length - 1]);
                out.println("");
            }
        }
    }

    private void saveFixationSequenceAsPng(final File outputPngFile) {
        final FixationSequence scanpath = new FixationSequence((int) gameContextScene.getWidth(),
            (int) gameContextScene.getHeight(), fixationSequence);
        try {
            scanpath.saveToFile(outputPngFile);
        } catch (final Exception e) {
            log.error("Exception", e);
        }
    }

    void incrementFixationSequence(final int x, final int y) {
        final long gazeDuration;

        final FixationPoint newGazePoint = new FixationPoint(System.currentTimeMillis(), 0, y, x);
        if (fixationSequence.size() != 0) {
            gazeDuration = newGazePoint.getTimeGaze()
                - (fixationSequence.get(fixationSequence.size() - 1)).getTimeGaze();
            newGazePoint.setGazeDuration(gazeDuration);
        }

        // if the new points coordinates are the same as last one's in the list then update the last fixationPoint in
        // the list
        // same coordinate points are a result of the eyetracker's frequency of sampling
        if (fixationSequence.size() > 1
            && (Math.abs(newGazePoint.getX()
            - fixationSequence.get(fixationSequence.size() - 1).getX()) <= fixationTrail)
            && (Math.abs(newGazePoint.getY()
            - fixationSequence.get(fixationSequence.size() - 1).getY()) <= fixationTrail)) {
            fixationSequence.get(fixationSequence.size() - 1)
                .setGazeDuration(newGazePoint.getGazeDuration() + newGazePoint.getGazeDuration());
        } else { // else add the new point in the list
            fixationSequence.add(newGazePoint);
        }
    }

    void incrementHeatMap(final int x, final int y) {
        currentGazeTime = System.currentTimeMillis();
        // in heatChart, x and y are opposed
        final int newX = (int) (y / heatMapPixelSize);
        final int newY = (int) (x / heatMapPixelSize);
        for (int i = -trail; i <= trail; i++) {
            for (int j = -trail; j <= trail; j++) {
                if (Math.sqrt(i * i + j * j) < trail) {
                    increment(newX + i, newY + j);
                }
            }
        }
    }

    private void increment(final int x, final int y) {
        if (heatMap != null && x >= 0 && y >= 0 && x < heatMap.length && y < heatMap[0].length) {
            heatMap[x][y]++;
        }
    }

    /**
     * @return the size of the HeatMap Pixel Size in order to avoid a too big heatmap (400 px) if maximum memory is more
     * than 1Gb, only 200
     */
    double computeHeatMapPixelSize(final Scene gameContextScene) {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final double width = gameContextScene.getWidth();
        final double result;
        if (maxMemory < 1024 * 1024 * 1024) {
            // size is less than 1Gb (2^30)
            result = width / 200;
        } else {
            result = width / 400;
        }
        log.info("computeHeatMapPixelSize() : result = {}", result);
        return result;
    }

    public void takeScreenShot() {
        gameScreenShot = gameContextScene.snapshot(null);
    }

}
