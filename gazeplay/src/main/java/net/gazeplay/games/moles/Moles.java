package net.gazeplay.games.moles;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Dimension2D;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.gazeplay.GameContext;
import net.gazeplay.GameLifeCycle;
import net.gazeplay.commons.configuration.Configuration;
import net.gazeplay.commons.configuration.ConfigurationBuilder;
import net.gazeplay.commons.utils.stats.Stats;
import javafx.scene.Parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

@Slf4j
public class Moles extends Parent implements GameLifeCycle {

    public final int nbHoles = 10;

    public final int timeGame = 60000; // Game duration = 1 minute

    private ProgressIndicator progressIndicator;

    @Data
    @AllArgsConstructor
    public class RoundDetails {
        public final List<MolesChar> molesList;
    }

    final GameContext gameContext;

    private final Stats stats;

    public Rectangle terrain;

    public int nbMolesWacked;

    public int nbMolesOut;

    private Label lab;

    public RoundDetails currentRoundDetails;

    public Moles(GameContext gameContext, Stats stats) {
        super();

        this.gameContext = gameContext;
        this.stats = stats;

        Dimension2D dimension2D = gameContext.getGamePanelDimensionProvider().getDimension2D();
        log.info("dimension2D = {}", dimension2D);

    }

    @Override
    public void launch() {

        Dimension2D dimension2D = gameContext.getGamePanelDimensionProvider().getDimension2D();
        Configuration config = ConfigurationBuilder.createFromPropertiesResource().build();
        
        Rectangle imageFond = new Rectangle(0, 0, dimension2D.getWidth(), dimension2D.getHeight());
        imageFond.setFill(new ImagePattern(new Image("data/wackmole/images/terrainTaupes.jpg")));
        gameContext.getChildren().add(imageFond);

        List<MolesChar> molesList = initMoles(config);
        currentRoundDetails = new RoundDetails(molesList);
        this.getChildren().addAll(molesList);
        gameContext.getChildren().add(this);

        Rectangle imageFondTrans = new Rectangle(0, 0, dimension2D.getWidth(), dimension2D.getHeight());
        imageFondTrans.setFill(new ImagePattern(new Image("data/wackmole/images/terrainTaupesTransparence.png")));
        gameContext.getChildren().add(imageFondTrans);

        this.nbMolesWacked = 0;
        
        /* Score display */
        lab = new Label();
        String s = "Score:" + nbMolesWacked;
        lab.setText(s);
        lab.setTextFill(Color.WHITE);
        lab.setFont(Font.font(dimension2D.getHeight() / 14));
        lab.setLineSpacing(10);
        lab.setLayoutX(0.4 * dimension2D.getWidth());
        lab.setLayoutY(0.08 * dimension2D.getHeight());
        gameContext.getChildren().add(lab);

        stats.notifyNewRoundReady();
        this.gameContext.resetBordersToFront();

        play(dimension2D);

    }

    /* Moles get out randomly */
    private void play(Dimension2D gameDim2D) {

        nbMolesOut = 0;
        Random r = new Random();
        
        long tmax = System.currentTimeMillis() + 6000;


        Timer minuteur = new Timer();
        TimerTask tache = new TimerTask() {
            public void run() {

                if (nbMolesOut < 3) {
                    chooseMoleToOut(r);
                } else if ((r.nextInt() % 5 == 0) && (nbMolesOut <= 4)) {
                    chooseMoleToOut(r);
                } else if ((r.nextInt() % 10 == 0) && (nbMolesOut <= 6)) {
                    chooseMoleToOut(r);
                } else if ((r.nextInt() % 20 == 0) && (nbMolesOut <= 8)) {
                    chooseMoleToOut(r);
                }
                
                /*if(System.currentTimeMillis() > tmax) {
                	minuteur.ha
                	ScoreTransition(gameDim2D);
                }*/
            }
        };
        
        minuteur.schedule(tache, 0, 500);
        
    	

        
    }

    private ProgressIndicator createProgressIndicator(javafx.geometry.Dimension2D gameDim2D) {
        ProgressIndicator indicator = new ProgressIndicator(1);
        indicator.setTranslateX(gameDim2D.getWidth() - gameDim2D.getWidth() * 0.07);
        indicator.setTranslateY(gameDim2D.getHeight() * 0.035);
        indicator.setMinWidth(computeMoleWidth(gameDim2D) * 0.45);
        indicator.setMinHeight(computeMoleWidth(gameDim2D) * 0.45);
        indicator.setOpacity(1);
        indicator.setProgress(0);
        indicator.setStyle(" -fx-progress-color: rgba(139,69,19 ,1);");
        
        return indicator;
    }

    @Override
    public void dispose() {
        if (currentRoundDetails != null) {
            if (currentRoundDetails.molesList != null) {
                gameContext.getChildren().removeAll(currentRoundDetails.molesList);
                currentRoundDetails.molesList.removeAll(currentRoundDetails.molesList);
            }
            currentRoundDetails = null;
        }
    }

    /* Select a mole not out for the moment and call "getOut()" */
    public void chooseMoleToOut(Random r) {
        if (this.currentRoundDetails == null) {
            return;
        }
        int indice;
        do {
            indice = r.nextInt(nbHoles);
        } while (!currentRoundDetails.molesList.get(indice).canGoOut);
        MolesChar m = currentRoundDetails.molesList.get(indice);
        m.getOut();
    }

    private double[][] CreationTableauPlacement(double width, double height, double distTrans) {
        double tabPlacement[][] = new double[10][2];

        tabPlacement[0][0] = 0.05 * width;
        tabPlacement[0][1] = 0.190 * height + distTrans;
        tabPlacement[1][0] = 0.382 * width;
        tabPlacement[1][1] = 0.185 * height + distTrans;
        tabPlacement[2][0] = 0.75 * width;
        tabPlacement[2][1] = 0.097 * height + distTrans;
        tabPlacement[3][0] = 0.22 * width;
        tabPlacement[3][1] = 0.345 * height + distTrans;
        tabPlacement[4][0] = 0.62 * width;
        tabPlacement[4][1] = 0.29 * height + distTrans;
        tabPlacement[5][0] = 0.468 * width;
        tabPlacement[5][1] = 0.465 * height + distTrans;
        tabPlacement[6][0] = 0.837 * width;
        tabPlacement[6][1] = 0.42 * height + distTrans;
        tabPlacement[7][0] = 0.059 * width;
        tabPlacement[7][1] = 0.531 * height + distTrans;
        tabPlacement[8][0] = 0.28 * width;
        tabPlacement[8][1] = 0.63 * height + distTrans;
        tabPlacement[9][0] = 0.67 * width;
        tabPlacement[9][1] = 0.59 * height + distTrans;

        return tabPlacement;
    }

    private List<MolesChar> initMoles(Configuration config) {
        javafx.geometry.Dimension2D gameDimension2D = gameContext.getGamePanelDimensionProvider().getDimension2D();

        ArrayList<MolesChar> result = new ArrayList<>();

        double moleHeight = computeMoleHeight(gameDimension2D);
        double moleWidth = computeMoleWidth(gameDimension2D);
        double height = gameDimension2D.getHeight();
        double width = gameDimension2D.getWidth();
        double distTrans = computeDistTransMole(gameDimension2D);

        double place[][] = CreationTableauPlacement(width, height, distTrans);

        /* Creation and placement of moles in the field */
        for (int i = 0; i < place.length; i++) {
            result.add(new MolesChar(place[i][0], place[i][1], moleWidth, moleHeight, distTrans, gameContext, stats,
                    this));
        }

        return result;
    }

    private static double computeDistTransMole(Dimension2D gameDimension2D) {
        return gameDimension2D.getHeight() * 0.16;
    }

    private static double computeMoleHeight(Dimension2D gameDimension2D) {
        return gameDimension2D.getHeight() * 0.14;
    }

    private static double computeMoleWidth(Dimension2D gameDimension2D) {
        return gameDimension2D.getWidth() * 0.13;
    }

    public void OneMoleWacked() {
        nbMolesWacked++;
        String s = "Score:" + nbMolesWacked;
        lab.setText(s);
    }
    
    /*private void ScoreTransition(Dimension2D dimension2D) {
    	
        Label l = new Label();
        String s = "Score:" + nbMolesWacked;
        l.setText(s);
        l.setTextFill(Color.RED);
        l.setFont(Font.font(dimension2D.getHeight() / 10));
        l.setLineSpacing(10);
        l.setLayoutX(0.5 * dimension2D.getWidth());
        l.setLayoutY(0.1 * dimension2D.getHeight());
        gameContext.getChildren().add(l);
    	
        TranslateTransition translation = new TranslateTransition(new Duration(6000), l);
        translation.setByX(0);
        translation.setByY(- dimension2D.getHeight() * 0.9);
        translation.play();
       
        

        translation.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                dispose();

                gameContext.clear();

                launch();
                
                //stats.notifyNewRoundReady();

                //gameContext.onGameStarted();
            }
        });
    }*/

}
