package business.system.dto;

/** A loan note as shown in the notes list, formatted for the UI layer. The text is not included. */
public record NoteRow(long id, String type, String author, String dateAdded) {}