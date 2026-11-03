// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.*;

/** Main application router for normal games and the puzzle workspace. */
public final class ChessWindow extends Application {
    private GameView game;
    private PuzzleView puzzles;
    private Stage window;
    private SetupDialog.Settings lastSettings=new SetupDialog.Settings(0,1500,BoardTheme.SAGE);
    @Override public void start(Stage stage) {
        window=stage;
        stage.setTitle("KingFischer 2.0 · Your chess room");
        showWelcome(lastSettings);
        stage.setOnCloseRequest(e->closeViews());stage.show();
    }
    private void scene(Scene scene,double minimumWidth,double minimumHeight) {
        Ui.style(scene);window.setMinWidth(minimumWidth);window.setMinHeight(minimumHeight);
        window.setScene(scene);window.sizeToScene();window.centerOnScreen();
    }
    private void closeViews() {
        if(game!=null){game.close();game=null;}
        if(puzzles!=null){puzzles.close();puzzles=null;}
    }
    private void showWelcome(SetupDialog.Settings settings) {
        closeViews();lastSettings=settings;
        scene(new Scene(SetupDialog.content(settings,this::startGame,null,this::startPuzzles),900,740),860,700);
    }
    private void startGame(SetupDialog.Settings settings) {
        closeViews();lastSettings=settings;
        game=new GameView(settings,this::newGame);
        scene(new Scene(game,1160,840),900,760);game.begin();
    }
    private void startPuzzles() {
        closeViews();
        puzzles=new PuzzleView(lastSettings.theme(),()->showWelcome(lastSettings));
        scene(new Scene(puzzles,1160,840),900,720);
    }
    private void newGame(SetupDialog.Settings settings) {
        lastSettings=settings;
        Stage setup=new Stage();setup.initOwner(window);setup.initModality(Modality.WINDOW_MODAL);
        setup.setTitle("KingFischer 2.0 · New game");setup.setResizable(false);
        Scene s=new Scene(SetupDialog.content(settings,next->{setup.close();startGame(next);},
            setup::close,()->{setup.close();startPuzzles();}),900,760);
        Ui.style(s);setup.setScene(s);setup.show();
    }
    @Override public void stop(){closeViews();}
    public static void launchGui(){Application.launch(ChessWindow.class);}
}
