package net.gazeplay.ui.scenes.configuration;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.*;
import lombok.extern.slf4j.Slf4j;
import net.gazeplay.GazePlay;
import net.gazeplay.commons.configuration.Configuration;
import net.gazeplay.commons.ui.I18NButton;
import net.gazeplay.commons.ui.Translator;
import net.gazeplay.commons.utils.games.GazePlayDirectories;
import net.gazeplay.commons.utils.games.ImageLibrary;
import net.gazeplay.commons.utils.games.ImageUtils;
import net.gazeplay.commons.utils.games.Utils;
import net.gazeplay.commons.utils.multilinguism.LanguageDetails;
import net.gazeplay.commons.utils.multilinguism.Languages;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

@Slf4j
public class CustomFileChooser extends Stage {

    private Configuration configuration;
    private ConfigurationContext configurationContext;
    private Translator translator;
    private GazePlay gazePlay;

    private FlowPane[] flowPanes = new FlowPane[3];
    private String[] folder = {"magiccards", "portraits", "blocs"};


    CustomFileChooser(Configuration configuration,
                      ConfigurationContext configurationContext,
                      Translator translator,
                      GazePlay gazePlay) {
        super();
        this.configuration = configuration;
        this.configurationContext = configurationContext;
        this.translator = translator;
        this.gazePlay = gazePlay;

        this.setMinWidth(500);
        this.setMinHeight(500);

        final Stage primaryStage = gazePlay.getPrimaryStage();
        this.initOwner(primaryStage);
        this.initModality(Modality.WINDOW_MODAL);
        this.initStyle(StageStyle.UTILITY);
        this.setOnCloseRequest(
            windowEvent -> primaryStage.getScene().getRoot().setEffect(null));
        this.setTitle(translator.translate("customFileChooser"));
        this.toFront();

        buildCustomColorDialog();
    }

    private void buildCustomColorDialog() {
        final BorderPane imageSelector = createImageSelectorPane();
        final Scene scene = new Scene(imageSelector, 500, 500, Color.TRANSPARENT);
        imageSelector.prefWidthProperty().bind(scene.widthProperty());
        imageSelector.prefHeightProperty().bind(scene.heightProperty());
        this.setScene(scene);
    }

    private DropShadow createNewDropShadow() {
        DropShadow dropShadowRight = new DropShadow();
        dropShadowRight.setRadius(10);
        dropShadowRight.setOffsetX(0);
        dropShadowRight.setOffsetY(0);
        dropShadowRight.setColor(Color.color(0, 0, 0, 0.5));
        return dropShadowRight;
    }

    private void updateFlow(int flowPaneIndex) {
        flowPanes[flowPaneIndex].getChildren().clear();

        List<Image> allImagesList = new LinkedList<>();
        ImageLibrary lib = ImageUtils.createImageLibrary(Utils.getImagesSubDirectory(folder[flowPaneIndex]));
        allImagesList.addAll(lib.pickAllImages());

        for (Image i : allImagesList) {
            StackPane preview = new StackPane();

            Rectangle backgroundPreview = new Rectangle(110, 110);
            backgroundPreview.setFill(Color.WHITE);

            ImageView imagePreview = new ImageView(i);
            double imageRatio = i.getWidth() / i.getHeight();
            imagePreview.setFitWidth(imageRatio > 1 ? 100 : 100 * imageRatio);
            imagePreview.setFitHeight(imageRatio > 1 ? 100 / imageRatio : 100);

            Button delete = new Button("x");
            delete.setPrefWidth(10);
            delete.setPrefHeight(10);
            StackPane.setAlignment(delete, Pos.TOP_RIGHT);
            delete.addEventHandler(MouseEvent.MOUSE_CLICKED, (EventHandler<Event>) event -> {
                final Stage dialog = removeDialogue(flowPaneIndex, i, preview);

                final String dialogTitle = translator.translate("Remove");
                dialog.setTitle(dialogTitle);

                dialog.toFront();

                dialog.show();
                this.getScene().getRoot().setEffect(new GaussianBlur());
            });

            preview.getChildren().addAll(backgroundPreview, imagePreview, delete);
            flowPanes[flowPaneIndex].getChildren().add(preview);
        }
    }

    private Stage removeDialogue(int index, Image i, StackPane preview) {
        final Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(this);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setOnCloseRequest(windowEvent -> this.getScene().getRoot().setEffect(null));


        final Button yes = new Button(translator.translate("YesRemove"));
        yes.getStyleClass().add("gameChooserButton");
        yes.getStyleClass().add("gameVariation");
        yes.getStyleClass().add("button");
        yes.setMinHeight(gazePlay.getPrimaryStage().getHeight() / 10);
        yes.setMinWidth(gazePlay.getPrimaryStage().getWidth() / 10);
        yes.addEventFilter(MouseEvent.MOUSE_CLICKED, (EventHandler<Event>) event -> {
            try {
                URI url = new URI(i.getUrl());
                final File imageToDelete = new File(url.getPath());
                try {
                    if (Desktop.getDesktop().moveToTrash(imageToDelete)) {
                        flowPanes[index].getChildren().remove(preview);
                    } else {
                        log.info("the file {} can't be moved to trash", i.getUrl());
                    }
                } catch (Exception e) {
                    log.info("the file {} can't be moved to trash", i.getUrl());
                } finally {
                    if (imageToDelete.delete()) {
                        flowPanes[index].getChildren().remove(preview);
                    } else {
                        log.info("the file {} can't be deleted", i.getUrl());
                    }
                }
            } catch (URISyntaxException e) {
                log.info("the file {} can't be deleted", i.getUrl());
            }
            closeDialog(dialog);
        });

        final Button no = new Button(translator.translate("NoCancel"));
        no.getStyleClass().add("gameChooserButton");
        no.getStyleClass().add("gameVariation");
        no.getStyleClass().add("button");
        no.setMinHeight(gazePlay.getPrimaryStage().getHeight() / 10);
        no.setMinWidth(gazePlay.getPrimaryStage().getWidth() / 10);
        no.addEventFilter(MouseEvent.MOUSE_CLICKED, (EventHandler<Event>) event -> {
            closeDialog(dialog);
        });

        final HBox choicePane = new HBox();
        choicePane.setSpacing(20);
        choicePane.setAlignment(Pos.CENTER);

        choicePane.getChildren().addAll(yes, no);

        final ScrollPane choicePanelScroller = new ScrollPane(choicePane);
        choicePanelScroller.setMinHeight(gazePlay.getPrimaryStage().getHeight() / 3);
        choicePanelScroller.setMinWidth(gazePlay.getPrimaryStage().getWidth() / 3);
        choicePanelScroller.setFitToWidth(true);
        choicePanelScroller.setFitToHeight(true);

        final Scene scene = new Scene(choicePanelScroller, Color.TRANSPARENT);
        dialog.setScene(scene);
        return dialog;
    }

    void closeDialog(Stage dialog) {
        this.getScene().getRoot().setEffect(null);
        dialog.close();
    }

    private I18NButton createAddButton(int flowPaneIndex) {
        I18NButton add = new I18NButton(translator, "addNewImages");
        add.setPrefHeight(10);
        add.setOnAction(e -> {
            String folderPath = Utils.getImagesSubDirectory(folder[flowPaneIndex]).getAbsolutePath();
            File dir = new File(folderPath);
            if (dir.mkdirs() || dir.exists()) {
                final FileChooser fileChooser = new FileChooser();
                fileChooser.setInitialDirectory(
                    new File(folderPath)
                );
                List<File> files =
                    fileChooser.showOpenMultipleDialog(this);
                if (files != null) {
                    for (File f : files) {
                        copyFile(dir, f, flowPaneIndex);
                    }
                }
            } else {
                log.debug("File {} doesn't exist and can't be created", dir.getAbsolutePath());
            }
            updateFlow(flowPaneIndex);
        });
        return add;
    }

    private void copyFile(File dir, File f, int flowPaneIndex) {
        File dest = new File(dir, f.getName());
        try {
            Files.copy(f.toPath(), dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.debug("Can't copy file {} to {}", f.getName(), dir.getAbsolutePath());
        }
    }

    private BorderPane createImageSelectorPane() {
        BorderPane imageSelectorGridPane = new BorderPane();

        Group[] group = new Group[3];
        StackPane[] section = new StackPane[3];
        Color[] colors = {Color.PALEGOLDENROD, Color.LIGHTSEAGREEN, Color.PALEVIOLETRED};
        String[] imagePath = {
            "data/common/images/cards.png",
            "data/common/images/small.png",
            "data/common/images/large.png"
        };

        HBox input = buildFileChooser();
        input.setPadding(new Insets(20, 0, 20, 0));
        input.setAlignment(Pos.CENTER);
        imageSelectorGridPane.setTop(input);

        DropShadow dropShadow = createNewDropShadow();

        for (int i = 0; i < 3; i++) {
            int index = i;
            group[i] = new Group();

            BorderPane background = new BorderPane();
            background.layoutYProperty().bind(input.heightProperty().add(50));
            background.setLayoutX(0);
            background.prefWidthProperty().bind(imageSelectorGridPane.widthProperty());
            background.prefHeightProperty().bind(imageSelectorGridPane.heightProperty().subtract(50).subtract(input.heightProperty()));
            background.setBackground(new Background(new BackgroundFill(colors[i], CornerRadii.EMPTY, null)));

            flowPanes[i] = new FlowPane();
            flowPanes[i].setAlignment(Pos.CENTER);
            flowPanes[i].setHgap(10);
            flowPanes[i].setVgap(10);
            flowPanes[i].setPadding(new Insets(20, 20, 20, 20));
            flowPanes[i].setBackground(new Background(new BackgroundFill(colors[i], CornerRadii.EMPTY, null)));

            ScrollPane scrollPane = new ScrollPane(flowPanes[i]);
            scrollPane.setBackground(new Background(new BackgroundFill(colors[i], CornerRadii.EMPTY, null)));
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);


            StackPane addButtonStackPane = new StackPane();
            Rectangle backgroundAddButton = new Rectangle();
            backgroundAddButton.widthProperty().bind(background.widthProperty());
            backgroundAddButton.setHeight(50);
            backgroundAddButton.setFill(colors[i]);
            I18NButton add = createAddButton(i);
            addButtonStackPane.getChildren().addAll(backgroundAddButton, add);
            BorderPane.setAlignment(addButtonStackPane, Pos.CENTER);
            background.setTop(addButtonStackPane);
            background.setCenter(scrollPane);

            section[i] = new StackPane();
            section[i].layoutYProperty().bind(input.heightProperty());
            Rectangle ongletBackground = new Rectangle();
            ongletBackground.setHeight(50);
            ongletBackground.widthProperty().bind(background.widthProperty().divide(3));
            ongletBackground.setFill(colors[i]);
            section[i].getChildren().add(ongletBackground);
            section[i].setOnMouseClicked(e -> {
                group[index].toFront();
            });

            group[i].getChildren().addAll(background, section[i]);
            group[i].setEffect(dropShadow);

            updateFlow(i);
            ImageView sectionLogo = new ImageView(new Image(imagePath[i]));
            sectionLogo.setPreserveRatio(true);
            sectionLogo.setFitHeight(30);
            Label sectionLabel = new Label(folder[i]);
            HBox title = new HBox(sectionLogo, sectionLabel);
            title.setSpacing(10);
            title.setAlignment(Pos.CENTER);
            StackPane.setAlignment(title, Pos.CENTER);
            section[i].getChildren().add(title);
        }

        configuration.getFiledirProperty().addListener(e -> {
            updateFlows();
        });


        section[0].setLayoutX(0);
        section[1].layoutXProperty().bind(section[0].widthProperty());
        section[2].layoutXProperty().bind(section[0].widthProperty().add(section[1].widthProperty()));

        imageSelectorGridPane.getChildren().addAll(group);
        return imageSelectorGridPane;
    }

    private void updateFlows() {
        updateFlow(0);
        updateFlow(1);
        updateFlow(2);
    }

    private HBox buildFileChooser() {
        final HBox pane = new HBox(5);
        final String fileDir;
        Button buttonLoad;

        Locale currentLocale = translator.currentLocale();
        LanguageDetails languageDetails = Languages.getLocale(currentLocale);
        if (!languageDetails.isLeftAligned()) {
            pane.setAlignment(Pos.BASELINE_RIGHT);
        }

        fileDir = configuration.getFileDir();

        buttonLoad = new Button(fileDir);

        buttonLoad.setOnAction(arg0 -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            final File currentFolder;
            currentFolder = new File(configuration.getFileDir());
            if (currentFolder.isDirectory()) {
                directoryChooser.setInitialDirectory(currentFolder);
            }
            final GazePlay gazePlay = configurationContext.getGazePlay();
            final Scene scene = gazePlay.getPrimaryScene();
            File file = directoryChooser.showDialog(scene.getWindow());
            if (file == null) {
                return;
            }
            String newPropertyValue = file.getAbsolutePath();
            if (Utils.isWindows()) {
                newPropertyValue = Utils.convertWindowsPath(newPropertyValue);
            }
            buttonLoad.textProperty().setValue(newPropertyValue);
            configuration.getFiledirProperty().setValue(newPropertyValue);
            updateFlows();
        });
        final I18NButton resetButton = new I18NButton(translator, "reset");
        resetButton.setOnAction(
            e -> {
                String defaultValue = "";
                String username = configuration.getUserName();
                if (username.equals("")) {
                    defaultValue = GazePlayDirectories.getDefaultFileDirectoryDefaultValue().getAbsolutePath();
                } else {
                    defaultValue = GazePlayDirectories.getFileDirectoryUserValue(username).getAbsolutePath();
                }
                configuration.getFiledirProperty().setValue(defaultValue);
                buttonLoad.textProperty().setValue(defaultValue);
                updateFlows();
            });
        pane.getChildren().addAll(buttonLoad, resetButton);
        return pane;
    }
}
