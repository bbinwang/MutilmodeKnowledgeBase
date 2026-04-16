package com.multimode.kb.presentation.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.multimode.kb.R;
import com.multimode.kb.domain.entity.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    private List<SearchResult> results = new ArrayList<>();

    public void setResults(List<SearchResult> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResult result = results.get(position);
        holder.textDocName.setText(result.getDocumentName());
        holder.textSource.setText(result.getSourceLocation());
        holder.textSnippet.setText(result.getText());

        // Show match type badges
        StringBuilder matchTypes = new StringBuilder();
        if (result.hasVectorMatch()) matchTypes.append("语义 ");
        if (result.hasKeywordMatch()) matchTypes.append("关键词");
        holder.textMatchType.setText(matchTypes.toString().trim());
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textDocName;
        final TextView textSource;
        final TextView textSnippet;
        final TextView textMatchType;

        ViewHolder(View view) {
            super(view);
            textDocName = view.findViewById(R.id.text_doc_name);
            textSource = view.findViewById(R.id.text_source);
            textSnippet = view.findViewById(R.id.text_snippet);
            textMatchType = view.findViewById(R.id.text_match_type);
        }
    }
}
