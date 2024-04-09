package dev.fileeditor.votl.objects.logs;

import java.util.List;

import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.objects.annotation.Nullable;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.DiffRow.Tag;

public class MessageData {
	private final String content, authorName;
	private final Attachment attachment;
	private final long authorId;

	public MessageData(Message message) {
		this.content = message.getContentRaw();
		if (message.getAttachments().isEmpty())
			this.attachment = null;
		else
			this.attachment = message.getAttachments().get(0);
		this.authorId = message.getAuthor().getIdLong();
		this.authorName = message.getAuthor().getName();
	}

	public String getContent() {
		return content;
	}

	public String getContentStripped() {
		return MarkdownSanitizer.sanitize(content);
	}

	public String getContentEscaped() {
		return MarkdownSanitizer.escape(content);
	}

	public Attachment getAttachment() {
		return attachment;
	}

	public long getAuthorId() {
		return authorId;
	}

	public String getAuthorName() {
		return authorName;
	}

	public boolean isEmpty() {
		return content.isBlank() && attachment == null;
	}

	@Nullable
	public static String getDiffContent(@Nonnull String oldContent, @Nonnull String newContent) {
		if (oldContent.equals(newContent)) return null;
		DiffRowGenerator generator = DiffRowGenerator.create()
			.showInlineDiffs(true)
			.inlineDiffByWord(true)
			.ignoreWhiteSpaces(true)
			.lineNormalizer(f -> f)
			.newTag(f -> "")
			.oldTag(f -> "")
			.build();
		List<DiffRow> rows = generator.generateDiffRows(
			List.of(oldContent.split("\\n")),
			List.of(newContent.split("\\n"))
		);
		
		StringBuffer diff = new StringBuffer();
		boolean skipped = false;
		final int size = rows.size();
		for (int i = 0; i<size; i++) {
			DiffRow row = rows.get(i);
			if (row.getTag().equals(Tag.EQUAL)) {
				if ((i+1 >= size || rows.get(i+1).getTag().equals(Tag.EQUAL))
					&& (i-1 < 0 || rows.get(i-1).getTag().equals(Tag.EQUAL)))
				{
					skipped = true;
					continue;
				}
			}
			if (skipped) {
				diff.append(" ...\n");
				skipped = false;
			}

			switch (row.getTag()) {
				case INSERT -> diff.append("+ "+row.getNewLine());
				case DELETE -> diff.append("- "+row.getOldLine());
				case CHANGE -> {
					diff.append("- "+row.getOldLine())
						.append("\n")
						.append("+ "+row.getNewLine());
				}
				default -> diff.append(" "+row.getOldLine());
			}
			diff.append("\n");
		}
		return diff.toString();
	}
}
