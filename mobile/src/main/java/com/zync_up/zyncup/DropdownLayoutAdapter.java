/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zync_up.zyncup;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

/**
 * Class that inflates and binds the views in a dropdown list from
 * the Searchbar.
 */
public class DropdownLayoutAdapter {

    private final LayoutInflater mInflater;
    private final Context mContext;

    public DropdownLayoutAdapter(LayoutInflater inflater, Context context) {
        mInflater = inflater;
        mContext = context;
    }

    /**
     * Layouts and binds recipient information to the view. If convertView is null, inflates a new
     * view with getItemLayout().
     *
     * @param convertView The view to bind information to.
     * @param parent The parent to bind the view to if we inflate a new view.
     * @param searchResult The SearchResult to get information from.
     *
     * @return A view ready to be shown in the drop down list.
     */
    public View bindView(View convertView, ViewGroup parent, SearchResult searchResult) {
        // Default to show all the information
        String searchResultName = searchResult.getName();
        Uri searchResultPhoto = searchResult.getPhoto();

        final View itemView = reuseOrInflateView(convertView, parent);

        final ViewHolder viewHolder = new ViewHolder(itemView);

        // Bind the information to the view
        bindTextToView(searchResultName, viewHolder.textViewName);
        bindIconToView(searchResultPhoto, viewHolder.imageViewPhoto);

        return itemView;
    }

    /**
     * Returns the same view, or inflates a new one if the given view was null.
     */
    protected View reuseOrInflateView(View convertView, ViewGroup parent) {
        int itemLayout = R.layout.search_list_item;
        return convertView != null ? convertView : mInflater.inflate(itemLayout, parent, false);
    }

    /**
     * Binds the text to the given text view. If the text was null, hides the text view.
     */
    protected void bindTextToView(CharSequence text, TextView view) {
        if (view == null) {
            return;
        }

        if (text != null) {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Binds the avatar icon to the image view. If we don't want to show the image, hides the
     * image view.
     */
    protected void bindIconToView(Uri searchResultPhoto, RoundedImageView view) {
        if (view == null) {
            return;
        }

        if (searchResultPhoto != null) {
            Picasso.with(mContext).load(searchResultPhoto).into(view);
        } else {
            view.setImageResource(R.drawable.ic_contact_picture);
        }
        view.setBorderWidth(0);
    }

    /**
     * A holder class the view. Uses the getters in DropdownLayoutAdapter to find the id of the
     * corresponding views.
     */
    protected class ViewHolder {
        public final TextView textViewName;
        public final RoundedImageView imageViewPhoto;

        public ViewHolder(View view) {
            textViewName = (TextView) view.findViewById(R.id.textview_search_item_name);
            imageViewPhoto = (RoundedImageView) view.findViewById(R.id.imageview_search_item_picture);
        }
    }
}
