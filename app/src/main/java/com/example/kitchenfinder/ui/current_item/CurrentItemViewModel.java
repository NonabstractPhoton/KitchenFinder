package com.example.kitchenfinder.ui.current_item;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CurrentItemViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public CurrentItemViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is current item fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}