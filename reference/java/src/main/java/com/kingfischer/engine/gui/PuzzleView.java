// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.chess.*;
import com.kingfischer.engine.movegen.MoveGenerator;
import com.kingfischer.engine.puzzles.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/** Puzzle board and sidebar. Disk work runs on one cancellable daemon worker. */
public final class PuzzleView extends BorderPane implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"KingFischer-puzzles"); t.setDaemon(true); return t;
    });
    private final Preferences prefs=Preferences.userNodeForPackage(PuzzleView.class);
    private final BoardPanel board=new BoardPanel(this::clicked);
    private final PuzzleSetupView setup;
    private final Label status=Ui.label("Choose a puzzle database", "status-title");
    private final Label detail=Ui.label("Select lichess_db_puzzle.csv to begin.", "muted");
    private final Label score=Ui.label("", "small-muted"), taskMessage=Ui.label("", "small-muted");
    private final ProgressBar progress=new ProgressBar();
    private final Button cancel=Ui.button("Cancel", "text-button", this::cancelWork);
    private final VBox taskBox=new VBox(6,taskMessage,progress,cancel);
    private final TextArea solution=new TextArea();
    private final VBox actions=new VBox(8);
    private final PauseTransition replyDelay=new PauseTransition(Duration.millis(450));
    private final PauseTransition filterDelay=new PauseTransition(Duration.millis(350));
    private PuzzleRepository repository;
    private PuzzleRepository.Matches matches;
    private PuzzleFilter matchedFilter;
    private PuzzleProgressStore history;
    private PuzzleSession session;
    private Task<?> active;
    private long generation, lastOffset=-1;
    private boolean closed,busy,flipped,recorded;
    private int selected=-1;
    private BoardTheme theme;
    public PuzzleView(BoardTheme initialTheme,Runnable home) {
        theme=initialTheme;
        getStyleClass().add("game-root");
        HBox nav=new HBox(20,Ui.label("♞  KingFischer 2.0", "brand"),Ui.label("PUZZLES","nav-active"),
            Ui.spacer(),Ui.button("Home", "secondary", home));
        nav.setAlignment(Pos.CENTER_LEFT); nav.setPadding(new Insets(14,24,14,24)); nav.getStyleClass().add("nav");
        setTop(nav);
        StackPane area=new StackPane(board); area.setMinSize(300,300);
        board.setMinSize(0,0);
        board.prefWidthProperty().bind(Bindings.min(area.widthProperty(),area.heightProperty()));
        board.prefHeightProperty().bind(board.prefWidthProperty());
        board.setMaxSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);
        HBox.setHgrow(area,Priority.ALWAYS);
        setup=new PuzzleSetupView(this::importDatabase,this::filtersChanged,this::nextPuzzle);
        status.setWrapText(true); detail.setWrapText(true); taskMessage.setWrapText(true);
        VBox state=new VBox(8,status,detail,score); state.getStyleClass().add("card"); state.setPadding(new Insets(16));
        HBox help=new HBox(8,Ui.button("Hint","secondary",this::hint),Ui.button("Retry","secondary",this::retry));
        Button reveal=Ui.button("Show solution","secondary",this::reveal);
        solution.setEditable(false); solution.setWrapText(true); solution.setPrefRowCount(3);
        solution.setVisible(false); solution.setManaged(false);
        ComboBox<BoardTheme> boardThemes=new ComboBox<>(); boardThemes.getItems().setAll(BoardTheme.values());
        boardThemes.setValue(theme); boardThemes.setMaxWidth(Double.MAX_VALUE);
        boardThemes.setOnAction(e->{theme=boardThemes.getValue();refresh();});
        actions.getChildren().addAll(help,reveal,solution,Ui.button("Flip board","text-button",()->{flipped=!flipped;refresh();}),boardThemes);
        actions.setPadding(new Insets(0,16,16,16)); actions.setDisable(true);
        progress.setMaxWidth(Double.MAX_VALUE); taskBox.setPadding(new Insets(10)); setTaskVisible(false);
        VBox side=new VBox(12,setup,taskBox,state,actions); side.setPrefWidth(330);
        ScrollPane scroll=new ScrollPane(side); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(355); scroll.setMinWidth(330); scroll.setMaxWidth(380);
        HBox body=new HBox(22,area,scroll); body.setPadding(new Insets(20)); setCenter(body);
        filterDelay.setOnFinished(e->search(false));
        replyDelay.setOnFinished(e->{
            if(closed || session==null || busy) return;
            int piece=session.complete()?0:session.position().board[Move.from(session.expectedMove())];
            int move=session.reply(); refresh(); if(move!=0) board.animate(move,piece); finishIfNeeded();
        });
        refresh();
        String saved=prefs.get("database", "");
        Path defaultFile=Path.of("data","puzzles","lichess_db_puzzle.csv").toAbsolutePath();
        Path candidate=saved.isBlank()?defaultFile:Path.of(saved);
        if(Files.isRegularFile(candidate)) Platform.runLater(()->{if(!closed) importDatabase(candidate);});
    }
    private void setTaskVisible(boolean visible) { taskBox.setVisible(visible); taskBox.setManaged(visible); }
    private void filtersChanged() {
        matches=null; matchedFilter=null; lastOffset=-1;
        setup.matching("Updating matching puzzles…");
        if(repository!=null && !busy) filterDelay.playFromStart();
    }
    private void importDatabase(Path path) {
        if(busy || closed) return;
        filterDelay.stop(); replyDelay.stop();
        record("FAILED"); session=null; matches=null; matchedFilter=null; repository=null; lastOffset=-1;
        status.setText("Opening puzzle database"); detail.setText("First import builds an index; future visits reuse it.");
        run(new Task<PuzzleRepository>() {
            protected PuzzleRepository call() throws Exception {
                if(history==null) history=new PuzzleProgressStore();
                return PuzzleRepository.open(path,this::isCancelled,(value,text)->{updateProgress(value,1);updateMessage(text);});
            }
        },repo->{
            repository=repo; prefs.put("database",path.toAbsolutePath().toString());
            setup.database(path,repo.themes(),repo.count());
            status.setText("Choose your challenge"); detail.setText("Set a theme and rating range, then start.");
            score(); filterDelay.playFromStart();
        });
    }
    private void nextPuzzle() {
        if(repository==null || busy) return;
        filterDelay.stop();
        PuzzleFilter filter;
        try { filter=setup.filter(); } catch(IllegalArgumentException e) { detail.setText(e.getMessage());return; }
        if(matches==null || !filter.equals(matchedFilter)) { search(true); return; }
        loadNext();
    }
    private void search(boolean load) {
        if(repository==null || busy || closed) return;
        PuzzleFilter filter;
        try { filter=setup.filter(); } catch(IllegalArgumentException e) { setup.matching(e.getMessage());return; }
        run(new Task<PuzzleRepository.Matches>() {
            protected PuzzleRepository.Matches call() throws Exception {
                updateMessage("Finding matching puzzles…");
                return repository.search(filter,this::isCancelled);
            }
        },found->{matches=found; matchedFilter=filter;
            setup.matching(String.format("%,d matching puzzles",found.count()));
            if(load) loadNext();
        });
    }
    private void loadNext() {
        if(matches==null || matches.count()==0) {
            detail.setText("No puzzles match. Try another theme or a wider rating range."); return;
        }
        replyDelay.stop();
        long[] offsets=matches.offsets();
        long offset=offsets[ThreadLocalRandom.current().nextInt(offsets.length)];
        if(offsets.length>1 && offset==lastOffset) {
            int i=Arrays.binarySearch(offsets,offset); offset=offsets[(i+1)%offsets.length];
        }
        record("FAILED");
        final long chosen=offset;
        run(new Task<PuzzleSession>() {
            protected PuzzleSession call() throws Exception {
                updateMessage("Loading puzzle…"); return new PuzzleSession(repository.load(chosen));
            }
        },loaded->{session=loaded;lastOffset=chosen;recorded=false;selected=-1;flipped=session.player()==1;
            solution.clear(); solution.setVisible(false);solution.setManaged(false);
            status.setText((session.player()==0?"White":"Black")+" to move");
            detail.setText("Puzzle "+session.puzzle().id()+" · Rating "+session.puzzle().rating());refresh();
        });
    }
    private <T> void run(Task<T> task, Consumer<T> success) {
        long ticket=++generation; active=task; busy=true;
        progress.progressProperty().unbind();taskMessage.textProperty().unbind();
        progress.progressProperty().bind(task.progressProperty());taskMessage.textProperty().bind(task.messageProperty());
        setTaskVisible(true); setup.busy(true,repository!=null);refresh();
        task.setOnSucceeded(e->{if(closed || ticket!=generation)return; endWork();success.accept(task.getValue());refresh();});
        task.setOnFailed(e->{if(closed || ticket!=generation)return;endWork();
            Throwable error=task.getException();status.setText("Could not complete that action");
            detail.setText(error.getMessage()==null?error.toString():error.getMessage());refresh();
        });
        task.setOnCancelled(e->{if(closed || ticket!=generation)return;endWork();detail.setText("Cancelled.");refresh();});
        worker.execute(task);
    }
    private void endWork() {
        active=null;busy=false;progress.progressProperty().unbind();taskMessage.textProperty().unbind();
        setTaskVisible(false);setup.busy(false,repository!=null);
    }
    private void cancelWork() {
        if(active!=null) active.cancel(true);
    }
    private void clicked(int square) {
        if(busy || session==null || !session.playerTurn() || square<0) return;
        Position p=session.position();
        if(selected>=0) {
            int[] legal=new int[256];int n=MoveGenerator.legal(p,legal);
            List<Integer> candidates=new ArrayList<>();
            for(int i=0;i<n;i++) if(Move.from(legal[i])==selected && Move.to(legal[i])==square) candidates.add(legal[i]);
            if(!candidates.isEmpty()) {
                int move=candidates.get(0);
                if(candidates.size()>1) {
                    ChoiceDialog<String> dialog=new ChoiceDialog<>("Queen",List.of("Queen","Rook","Bishop","Knight"));
                    dialog.initOwner(getScene().getWindow());dialog.setTitle("Promote pawn");dialog.setHeaderText("Choose promotion piece");
                    Optional<String> choice=dialog.showAndWait();if(choice.isEmpty())return;
                    int promotion=switch(choice.get()){case "Queen"->5;case "Rook"->4;case "Bishop"->3;default->2;};
                    move=candidates.stream().filter(m->Move.promotion(m)==promotion).findFirst().orElseThrow();
                }
                int piece=p.board[Move.from(move)];selected=-1;
                if(session.tryMove(move)) {
                    status.setText("Correct!"); refresh();board.animate(move,piece);
                    if(!session.complete()) replyDelay.playFromStart();finishIfNeeded();
                } else {status.setText("Try another move");detail.setText("That move is not the puzzle solution.");refresh();}
                return;
            }
        }
        selected=p.board[square]!=0 && (p.board[square]>>>3)==session.player()?square:-1;refresh();
    }
    private void hint() {
        if(session==null || !session.playerTurn())return;
        selected=session.hint();status.setText("Hint: move this piece");
        detail.setText("This attempt now counts as assisted.");refresh();
    }
    private void retry() {
        if(session==null)return;
        replyDelay.stop();record("FAILED");session.reset();recorded=false;selected=-1;
        solution.setVisible(false);solution.setManaged(false);board.stopAnimation();
        status.setText("Try again");detail.setText((session.player()==0?"White":"Black")+" to move");refresh();
    }
    private void reveal() {
        if(session==null)return;
        replyDelay.stop();session.reveal();record("REVEALED");
        Position p=new Position(session.puzzle().fen());p.make(p.parseMove(session.puzzle().moves().get(0)));
        StringBuilder line=new StringBuilder();
        for(String uci:session.puzzle().moves().subList(1,session.puzzle().moves().size())) {
            int move=p.parseMove(uci);
            line.append(p.fullmove).append(p.side==0?". ":"... ").append(MoveNotation.format(p,move,true)).append("  ");p.make(move);
        }
        solution.setText(line.toString());solution.setVisible(true);solution.setManaged(true);
        status.setText("Solution revealed");detail.setText("Retry to practise, or choose the next puzzle.");refresh();
    }
    private void finishIfNeeded() {
        if(session!=null && session.complete()) {
            status.setText(session.clean()?"Puzzle solved!":"Puzzle completed with help or retries");
            detail.setText("Choose Next puzzle for another challenge.");record(session.clean()?"SOLVED":"ASSISTED");refresh();
        }
    }
    private void record(String outcome) {
        if(recorded || session==null || history==null)return;
        recorded=true;String id=session.puzzle().id();
        worker.execute(()->{
            try {history.record(id,outcome);Platform.runLater(()->{if(!closed)score();});}
            catch(IOException e) {Platform.runLater(()->{if(!closed)detail.setText("Progress could not be saved: "+e.getMessage());});}
        });
    }
    private void score() {if(history!=null)score.setText(history.solvedCount()+" solved without help · "+history.attempts()+" recorded attempts");}
    private void refresh() {
        if(session!=null)board.show(session.position(),theme,flipped,selected,session.lastMove(),!busy && session.playerTurn());
        else board.show(new Position(),theme,false,-1,0,false);
        actions.setDisable(busy || session==null);
        // A search can temporarily disable the board during an automatic reply.
        if(!busy && session!=null && !session.complete() && !session.revealed() && !session.playerTurn()
            && replyDelay.getStatus()!=javafx.animation.Animation.Status.RUNNING) replyDelay.playFromStart();
    }
    @Override public void close() {
        record("FAILED");
        closed=true;generation++;filterDelay.stop();replyDelay.stop();board.stopAnimation();
        if(active!=null)active.cancel(true);
        // Queued progress writes are allowed to finish; UI tasks are cancelled above.
        worker.shutdown();
    }
}
