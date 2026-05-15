namespace Kairos.Api.Contracts;

public sealed record NoteResponse(
    Guid Id,
    Guid ClientId,
    Guid? FolderClientId,
    string Title,
    string MarkdownBody,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt);

public sealed record NoteFolderResponse(
    Guid Id,
    Guid ClientId,
    Guid? ParentClientId,
    string Name,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt);

public sealed record TagResponse(
    Guid Id,
    Guid ClientId,
    string Name,
    string NormalizedName,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt);

public sealed record NoteTagLinkResponse(
    Guid Id,
    Guid ClientId,
    Guid NoteClientId,
    Guid TagClientId,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt);

public sealed record TaskTagLinkResponse(
    Guid Id,
    Guid ClientId,
    Guid TaskClientId,
    Guid TagClientId,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt);

public sealed record NoteTaskReferenceResponse(Guid NoteClientId, Guid TaskClientId);
public sealed record NoteNoteReferenceResponse(Guid SourceNoteClientId, Guid TargetNoteClientId);

public sealed record NotesSyncResponse(
    IReadOnlyList<NoteResponse> Notes,
    IReadOnlyList<NoteFolderResponse> Folders,
    IReadOnlyList<TagResponse> Tags,
    IReadOnlyList<NoteTagLinkResponse> NoteTagLinks,
    IReadOnlyList<TaskTagLinkResponse> TaskTagLinks,
    IReadOnlyList<NoteTaskReferenceResponse> NoteTaskReferences,
    IReadOnlyList<NoteNoteReferenceResponse> NoteNoteReferences,
    DateTimeOffset Cursor);

public sealed record UpsertNoteRequest(
    Guid? ClientId,
    Guid? FolderClientId,
    string Title,
    string? MarkdownBody,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record UpdateNoteRequest(
    Guid? FolderClientId,
    string? Title,
    string? MarkdownBody,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record RestoreNoteRequest(
    Guid? FolderClientId,
    string Title,
    string? MarkdownBody,
    DateTimeOffset? BaseUpdatedAt);

public sealed record UpsertFolderRequest(
    Guid? ClientId,
    Guid? ParentClientId,
    string Name,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record UpdateFolderRequest(
    Guid? ParentClientId,
    string? Name,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record RestoreFolderRequest(
    Guid? ParentClientId,
    string Name,
    DateTimeOffset? BaseUpdatedAt);

public sealed record UpsertTagRequest(
    Guid? ClientId,
    string Name,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record UpdateTagRequest(
    string? Name,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record RestoreTagRequest(
    string Name,
    DateTimeOffset? BaseUpdatedAt);

public sealed record UpsertNoteTagLinkRequest(
    Guid? ClientId,
    Guid NoteClientId,
    Guid TagClientId,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record UpsertTaskTagLinkRequest(
    Guid? ClientId,
    Guid TaskClientId,
    Guid TagClientId,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record NoteConflictResponse(string Code, string Error, NoteResponse ServerNote);
public sealed record FolderConflictResponse(string Code, string Error, NoteFolderResponse ServerFolder);
public sealed record TagConflictResponse(string Code, string Error, TagResponse ServerTag);
public sealed record NoteTagLinkConflictResponse(string Code, string Error, NoteTagLinkResponse ServerLink);
public sealed record TaskTagLinkConflictResponse(string Code, string Error, TaskTagLinkResponse ServerLink);
