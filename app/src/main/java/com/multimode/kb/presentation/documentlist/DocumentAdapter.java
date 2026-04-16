package com.multimode.kb.presentation.documentlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.multimode.kb.R;
import com.multimode.kb.domain.entity.DocumentStatus;
import com.multimode.kb.domain.entity.KbDocument;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.ViewHolder> {

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private List<KbDocument> documents = new ArrayList<>();
    private final OnDocumentClickListener clickListener;
    private final OnDocumentDeleteListener deleteListener;

    public interface OnDocumentClickListener {
        void onClick(KbDocument document);
    }

    public interface OnDocumentDeleteListener {
        void onDelete(KbDocument document);
    }

    public DocumentAdapter(OnDocumentClickListener clickListener,
                           OnDocumentDeleteListener deleteListener) {
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    public void setDocuments(List<KbDocument> documents) {
        this.documents = documents;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KbDocument doc = documents.get(position);
        holder.textFileName.setText(doc.getFileName());
        holder.textDate.setText(dateFormat.format(new Date(doc.getCreatedAt())));
        holder.textStatus.setText(doc.getStatus().getDisplayName());
        holder.textSegments.setText(
                doc.getTotalSegments() > 0 ? doc.getTotalSegments() + " 段" : "");

        // Status color
        int colorRes;
        switch (doc.getStatus()) {
            case INGESTED: colorRes = R.color.status_ingested; break;
            case INGESTING: colorRes = R.color.status_ingesting; break;
            case ERROR: colorRes = R.color.status_error; break;
            default: colorRes = R.color.textSecondary; break;
        }
        holder.textStatus.setTextColor(holder.itemView.getContext().getColor(colorRes));

        holder.itemView.setOnClickListener(v -> clickListener.onClick(doc));
        holder.buttonDelete.setOnClickListener(v -> deleteListener.onDelete(doc));
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textFileName;
        final TextView textDate;
        final TextView textStatus;
        final TextView textSegments;
        final ImageButton buttonDelete;

        ViewHolder(View view) {
            super(view);
            textFileName = view.findViewById(R.id.text_file_name);
            textDate = view.findViewById(R.id.text_date);
            textStatus = view.findViewById(R.id.text_status);
            textSegments = view.findViewById(R.id.text_segments);
            buttonDelete = view.findViewById(R.id.button_delete);
        }
    }
}
