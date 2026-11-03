// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.puzzles.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** The database selector and grouped filters in the right-hand puzzle panel. */
public final class PuzzleSetupView extends VBox {
    private final ComboBox<String> category=new ComboBox<>(), theme=new ComboBox<>();
    private final Spinner<Integer> minimum=new Spinner<>(0,10000,1500,100);
    private final Spinner<Integer> maximum=new Spinner<>(0,10000,2000,100);
    private final Label fileLabel=Ui.label("Choose your extracted Lichess CSV.","small-muted");
    private final Label matches=Ui.label("No database loaded", "muted");
    private final Button browse;
    private final VBox filters=new VBox(8);
    private Set<String> available=Set.of();
    private boolean updating;
    private final Runnable changed;
    public PuzzleSetupView(Consumer<Path> choose, Runnable changed, Runnable next) {
        super(10); this.changed=changed;
        getStyleClass().add("card"); setPadding(new Insets(16));
        fileLabel.setWrapText(true); matches.setWrapText(true);
        browse=Ui.button("Choose puzzle database…","secondary",()->{
            FileChooser picker=new FileChooser(); picker.setTitle("Choose lichess_db_puzzle.csv");
            picker.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Lichess CSV", "*.csv"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
            java.io.File selected=picker.showOpenDialog(getScene().getWindow());
            if(selected!=null) choose.accept(selected.toPath());
        });
        browse.setMaxWidth(Double.MAX_VALUE);
        category.getItems().setAll("All categories"); category.setValue("All categories");
        theme.setConverter(new StringConverter<>() {
            public String toString(String s) { return s==null?"":PuzzleFilter.label(s); }
            public String fromString(String s) { return s; }
        });
        theme.getItems().add(""); theme.setValue("");
        category.setMaxWidth(Double.MAX_VALUE); theme.setMaxWidth(Double.MAX_VALUE);
        minimum.setEditable(true); maximum.setEditable(true);
        minimum.setPrefWidth(125); maximum.setPrefWidth(125);
        HBox range=new HBox(8,new VBox(4,Ui.label("Minimum","small-muted"),minimum),
            new VBox(4,Ui.label("Maximum","small-muted"),maximum));
        Button nextButton=Ui.button("Start / Next puzzle →","primary",next);
        nextButton.setMaxWidth(Double.MAX_VALUE);
        filters.getChildren().addAll(Ui.label("CATEGORY","eyebrow"),category,
            Ui.label("THEME","eyebrow"),theme,Ui.label("PUZZLE RATING","eyebrow"),range,matches,nextButton);
        getChildren().addAll(Ui.label("PUZZLE LIBRARY","eyebrow"),browse,fileLabel,filters);
        category.setOnAction(e->{ if(!updating) populateThemes(); });
        theme.setOnAction(e->{ if(!updating) changed.run(); });
        minimum.getEditor().textProperty().addListener((o,a,b)->{ if(!updating) changed.run(); });
        maximum.getEditor().textProperty().addListener((o,a,b)->{ if(!updating) changed.run(); });
        filters.setDisable(true);
    }
    public void database(Path path, Set<String> themes, long count) {
        available=new TreeSet<>(themes);
        fileLabel.setText(path.getFileName()+" · "+String.format("%,d puzzles",count));
        updating=true;
        TreeSet<String> groups=new TreeSet<>();
        for(String tag:available) if(!tag.isEmpty()) groups.add(PuzzleFilter.category(tag));
        category.getItems().setAll("All categories"); category.getItems().addAll(groups);
        category.setValue("All categories"); updating=false;
        filters.setDisable(false); populateThemes();
    }
    private void populateThemes() {
        updating=true;
        String previous=theme.getValue(); theme.getItems().clear(); theme.getItems().add("");
        available.stream().filter(t->!t.isEmpty()).filter(t->category.getValue().equals("All categories")
            || PuzzleFilter.category(t).equals(category.getValue()))
            .sorted(Comparator.comparing(PuzzleFilter::label)).forEach(theme.getItems()::add);
        // A category selects its first theme, rather than silently querying all categories.
        theme.setValue(theme.getItems().contains(previous) && previous!=null && !previous.isEmpty()
            ? previous : (category.getValue().equals("All categories") || theme.getItems().size()==1 ? "" : theme.getItems().get(1)));
        updating=false; changed.run();
    }
    public PuzzleFilter filter() {
        try { return new PuzzleFilter(theme.getValue(),Integer.parseInt(minimum.getEditor().getText().trim()),
            Integer.parseInt(maximum.getEditor().getText().trim())); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Enter whole numbers for both ratings."); }
    }
    public void matching(String text) { matches.setText(text); }
    public void busy(boolean busy, boolean ready) { browse.setDisable(busy); filters.setDisable(busy || !ready); }
}
