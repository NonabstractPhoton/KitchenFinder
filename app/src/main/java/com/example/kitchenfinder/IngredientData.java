package com.example.kitchenfinder;

import androidx.annotation.Nullable;

public class IngredientData
{
    public String title, text;

    public IngredientData(String title, String text)
    {
        this.title = title;
        this.text = text;
    }

    public IngredientData()
    {
        // Default required
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof IngredientData))
            return false;
        IngredientData data = ((IngredientData) obj);
        return data.text.equals(text) && data.title.equals(title);
    }
}
