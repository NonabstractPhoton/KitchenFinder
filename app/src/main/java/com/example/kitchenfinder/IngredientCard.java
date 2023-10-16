package com.example.kitchenfinder;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class IngredientCard extends IngredientData implements Cloneable
{
    private Bitmap image;
    private boolean favorite;
    public static final IngredientCard EMPTY = new IngredientCard("", "");

    public IngredientCard(String title, String text, Bitmap image)
    {
        super(title, text);
        this.image = image;
    }

    public IngredientCard(IngredientData data)
    {
        super(data.title, data.text);
    }

    public IngredientCard(String title, String text)
    {
        super(title, text);
    }

    public IngredientCard() {}

    public Bitmap getImage() {return image;}
    public void setImage(Bitmap image) {this.image = image;}
    public boolean getFavorite() {return favorite;}
    public void setFavorite(boolean newState) {favorite = newState;}

    @NonNull
    @Override
    public Object clone() throws CloneNotSupportedException {
        IngredientCard clone = (IngredientCard) super.clone();
        clone.title = (String.valueOf(clone.title));
        clone.text = (String.valueOf(clone.text));
        clone.setImage(clone.getImage().copy(clone.getImage().getConfig(), false));
        return clone;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("Title: %s, Text: %s, Image: %s", title, text, image);
    }
}
