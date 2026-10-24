package com.pos.components;

import com.pos.Branding;
import com.pos.utils.BrandAssets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

/** Logo above the name in the expanded sidebar; a compact logo in the narrow rail. */
public final class SidebarBrand extends VBox {
    private final ImageView logo=BrandAssets.logoMark(58);
    private final StackPane tile=new StackPane();
    private final Label name=new Label(Branding.BRAND_NAME);
    public SidebarBrand() {
        super(12);setAlignment(Pos.CENTER);setFillWidth(false);setMaxWidth(Region.USE_PREF_SIZE);
        tile.setStyle("-fx-background-color: transparent;");
        tile.setMaxSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);
        var clip=new javafx.scene.shape.Rectangle();clip.widthProperty().bind(tile.widthProperty());clip.heightProperty().bind(tile.heightProperty());
        clip.setArcWidth(28);clip.setArcHeight(28);tile.setClip(clip);
        if(logo!=null)tile.getChildren().add(logo);
        name.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: white;");
        getChildren().addAll(tile,name);setCollapsed(false);
    }
    public void setCollapsed(boolean collapsed) {
        name.setVisible(!collapsed);name.setManaged(!collapsed);
        tile.setPrefSize(collapsed?56:136,collapsed?44:84);
        if(logo!=null){
            logo.setFitHeight(collapsed?26:58);
            logo.setTranslateX(logo.getFitHeight()*logo.getImage().getWidth()/logo.getImage().getHeight()*0.193);
        }
    }
}
