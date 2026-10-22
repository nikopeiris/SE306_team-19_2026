package business.system.dto;

/** One loan note with its full text, formatted for the UI layer. */
public record NoteDetail(long id, String type, String lines, String author, String dateAdded) {}