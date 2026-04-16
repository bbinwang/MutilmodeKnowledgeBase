package com.multimode.kb.presentation.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.multimode.kb.R;
import com.multimode.kb.domain.entity.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_BOT = 1;

    private List<ChatViewModel.ChatMessage> messages = new ArrayList<>();
    private OnSourceClickListener sourceClickListener;

    public interface OnSourceClickListener {
        void onSourceClick(SearchResult source);
    }

    public void setMessages(List<ChatViewModel.ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void setSourceClickListener(OnSourceClickListener listener) {
        this.sourceClickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isFromBot ? TYPE_BOT : TYPE_USER;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_USER
                ? R.layout.item_chat_user
                : R.layout.item_chat_bot;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view, viewType == TYPE_BOT);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatViewModel.ChatMessage msg = messages.get(position);
        holder.textMessage.setText(msg.text);

        if (holder.sourcesLayout != null) {
            List<SearchResult> sources = msg.sources;
            if (sources != null && !sources.isEmpty()) {
                holder.sourcesLayout.setVisibility(View.VISIBLE);
                holder.sourcesAdapter.setSources(sources);
            } else {
                holder.sourcesLayout.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textMessage;
        final LinearLayout sourcesLayout;
        final SourceAdapter sourcesAdapter;

        ViewHolder(View view, boolean isBot) {
            super(view);
            textMessage = view.findViewById(R.id.text_message);

            if (isBot) {
                sourcesLayout = view.findViewById(R.id.layout_sources);
                RecyclerView recyclerSources = view.findViewById(R.id.recycler_sources);
                sourcesAdapter = new SourceAdapter(source -> {
                    if (sourceClickListener != null) {
                        sourceClickListener.onSourceClick(source);
                    }
                });
                recyclerSources.setLayoutManager(new LinearLayoutManager(view.getContext()));
                recyclerSources.setAdapter(sourcesAdapter);
            } else {
                sourcesLayout = null;
                sourcesAdapter = null;
            }
        }
    }

    /**
     * Inner adapter for source reference cards within a bot message.
     */
    static class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.SourceViewHolder> {

        private List<SearchResult> sources = new ArrayList<>();
        private final OnSourceClickListener clickListener;

        SourceAdapter(OnSourceClickListener clickListener) {
            this.clickListener = clickListener;
        }

        void setSources(List<SearchResult> sources) {
            this.sources = sources;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_source_card, parent, false);
            return new SourceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
            SearchResult source = sources.get(position);

            // Media type icon
            String icon = getMediaIcon(source.getDocumentMimeType());
            holder.textMediaIcon.setText(icon);

            // Document name
            holder.textSourceDoc.setText(source.getDocumentName());

            // Source location
            String location = formatLocation(source);
            holder.textSourceLocation.setText(location);

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onSourceClick(source);
                }
            });
        }

        @Override
        public int getItemCount() {
            return sources.size();
        }

        private String getMediaIcon(String mimeType) {
            if (mimeType == null) return "\uD83D\uDCC4"; // 📄
            if (mimeType.startsWith("image/")) return "\uD83D\uDDBC"; // 🖼
            if (mimeType.startsWith("video/")) return "\uD83C\uDFAC"; // 🎬
            if (mimeType.startsWith("audio/")) return "\uD83C\uDFB5"; // 🎵
            if (mimeType.contains("pdf")) return "\uD83D\uDCC4"; // 📄
            if (mimeType.contains("presentation") || mimeType.contains("pptx"))
                return "\uD83D\uDCCB"; // 📋
            if (mimeType.contains("docx") || mimeType.contains("word"))
                return "\uD83D\uDCDD"; // 📝
            return "\uD83D\uDCC4"; // 📄
        }

        private String formatLocation(SearchResult source) {
            String location = source.getSourceLocation();
            if (location != null && !location.isEmpty()) {
                return location;
            }
            // Fallback: try to extract page from metadataJson
            String meta = source.getMetadataJson();
            if (meta != null && meta.contains("\"page\"")) {
                try {
                    int idx = meta.indexOf("\"page\"") + 7;
                    int end = meta.indexOf(",", idx);
                    if (end == -1) end = meta.indexOf("}", idx);
                    return "Page " + meta.substring(idx, end).trim();
                } catch (Exception ignored) {
                }
            }
            return "";
        }

        static class SourceViewHolder extends RecyclerView.ViewHolder {
            final TextView textMediaIcon;
            final TextView textSourceDoc;
            final TextView textSourceLocation;

            SourceViewHolder(View view) {
                super(view);
                textMediaIcon = view.findViewById(R.id.text_media_icon);
                textSourceDoc = view.findViewById(R.id.text_source_doc);
                textSourceLocation = view.findViewById(R.id.text_source_location);
            }
        }
    }
}
