using System.Security.Claims;
using System.Text.RegularExpressions;
using Kairos.Api.Contracts;
using Kairos.Api.Data;
using Kairos.Api.Data.Entities;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace Kairos.Api.Controllers;

[ApiController]
[Authorize]
[Route("api")]
public sealed partial class NotesController(AppDbContext dbContext) : ControllerBase
{
    [HttpGet("notes/sync")]
    public async Task<NotesSyncResponse> GetSync(
        [FromQuery] DateTimeOffset? since = null,
        [FromQuery] bool includeDeleted = true,
        CancellationToken cancellationToken = default)
    {
        var userId = GetCurrentUserId();
        var notes = (await dbContext.Notes
            .Where(note => note.UserId == userId && (includeDeleted || note.DeletedAt == null) && (since == null || note.UpdatedAt > since.Value))
            .OrderBy(note => note.UpdatedAt)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
        var folders = (await dbContext.NoteFolders
            .Where(folder => folder.UserId == userId && (includeDeleted || folder.DeletedAt == null) && (since == null || folder.UpdatedAt > since.Value))
            .OrderBy(folder => folder.UpdatedAt)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
        var tags = (await dbContext.Tags
            .Where(tag => tag.UserId == userId && (includeDeleted || tag.DeletedAt == null) && (since == null || tag.UpdatedAt > since.Value))
            .OrderBy(tag => tag.UpdatedAt)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
        var noteTagLinks = (await dbContext.NoteTagLinks
            .Where(link => link.UserId == userId && (includeDeleted || link.DeletedAt == null) && (since == null || link.UpdatedAt > since.Value))
            .OrderBy(link => link.UpdatedAt)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
        var taskTagLinks = (await dbContext.TaskTagLinks
            .Where(link => link.UserId == userId && (includeDeleted || link.DeletedAt == null) && (since == null || link.UpdatedAt > since.Value))
            .OrderBy(link => link.UpdatedAt)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
        var noteTaskReferences = await dbContext.NoteTaskReferences
            .Where(reference => reference.UserId == userId)
            .Select(reference => new NoteTaskReferenceResponse(reference.NoteClientId, reference.TaskClientId))
            .ToListAsync(cancellationToken);
        var noteNoteReferences = await dbContext.NoteNoteReferences
            .Where(reference => reference.UserId == userId)
            .Select(reference => new NoteNoteReferenceResponse(reference.SourceNoteClientId, reference.TargetNoteClientId))
            .ToListAsync(cancellationToken);

        var cursor = new[]
        {
            notes.LastOrDefault()?.UpdatedAt,
            folders.LastOrDefault()?.UpdatedAt,
            tags.LastOrDefault()?.UpdatedAt,
            noteTagLinks.LastOrDefault()?.UpdatedAt,
            taskTagLinks.LastOrDefault()?.UpdatedAt
        }.Where(value => value is not null).Max() ?? since ?? DateTimeOffset.UtcNow;

        return new NotesSyncResponse(notes, folders, tags, noteTagLinks, taskTagLinks, noteTaskReferences, noteNoteReferences, cursor);
    }

    [HttpGet("notes")]
    public async Task<IReadOnlyList<NoteResponse>> GetNotes([FromQuery] bool includeDeleted = false, CancellationToken cancellationToken = default)
    {
        var userId = GetCurrentUserId();
        return (await dbContext.Notes
            .Where(note => note.UserId == userId && (includeDeleted || note.DeletedAt == null))
            .OrderByDescending(note => note.UpdatedAt)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
    }

    [HttpPost("notes")]
    public async Task<ActionResult<NoteResponse>> CreateNote(UpsertNoteRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Title)) return BadRequest(new { error = "Title is required." });
        var userId = GetCurrentUserId();
        var clientId = request.ClientId ?? Guid.NewGuid();
        var existing = await dbContext.Notes.SingleOrDefaultAsync(note => note.UserId == userId && note.ClientId == clientId, cancellationToken);
        if (existing is not null)
        {
            if (existing.DeletedAt is not null) return NoteConflict(existing, "note_deleted", "Note is deleted. Restore it before updating.");
            if (Matches(existing, request)) return Ok(ToResponse(existing));
            var conflict = Validate(existing, request.BaseUpdatedAt);
            if (conflict is not null) return conflict;
            Apply(existing, request);
            existing.UpdatedAt = DateTimeOffset.UtcNow;
            await RefreshNoteReferences(existing, cancellationToken);
            await dbContext.SaveChangesAsync(cancellationToken);
            return Ok(ToResponse(existing));
        }

        var now = DateTimeOffset.UtcNow;
        var note = new NoteItem
        {
            UserId = userId,
            ClientId = clientId,
            FolderClientId = request.FolderClientId,
            Title = request.Title.Trim(),
            MarkdownBody = request.MarkdownBody ?? string.Empty,
            CreatedAt = now,
            UpdatedAt = now
        };
        dbContext.Notes.Add(note);
        await RefreshNoteReferences(note, cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        return CreatedAtAction(nameof(GetNotes), new { id = note.Id }, ToResponse(note));
    }

    [HttpPatch("notes/{id:guid}")]
    public async Task<ActionResult<NoteResponse>> UpdateNote(Guid id, UpdateNoteRequest request, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var note = await dbContext.Notes.SingleOrDefaultAsync(note => note.UserId == userId && note.Id == id, cancellationToken);
        if (note is null) return NotFound();
        if (note.DeletedAt is not null) return NoteConflict(note, "note_deleted", "Note is deleted. Restore it before updating.");
        var conflict = Validate(note, request.BaseUpdatedAt);
        if (conflict is not null) return conflict;
        if (request.Title is not null)
        {
            if (string.IsNullOrWhiteSpace(request.Title)) return BadRequest(new { error = "Title cannot be blank." });
            note.Title = request.Title.Trim();
        }
        note.FolderClientId = request.FolderClientId;
        if (request.MarkdownBody is not null) note.MarkdownBody = request.MarkdownBody;
        note.UpdatedAt = DateTimeOffset.UtcNow;
        await RefreshNoteReferences(note, cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        return ToResponse(note);
    }

    [HttpDelete("notes/{id:guid}")]
    public async Task<ActionResult<NoteResponse>> DeleteNote(Guid id, [FromQuery] DateTimeOffset? baseUpdatedAt, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var note = await dbContext.Notes.SingleOrDefaultAsync(note => note.UserId == userId && note.Id == id, cancellationToken);
        if (note is null) return NotFound();
        var conflict = Validate(note, baseUpdatedAt);
        if (conflict is not null) return conflict;
        if (note.DeletedAt is null)
        {
            var now = DateTimeOffset.UtcNow;
            note.DeletedAt = now;
            note.UpdatedAt = now;
            await RefreshNoteReferences(note, cancellationToken);
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        return ToResponse(note);
    }

    [HttpPost("notes/client/{clientId:guid}/restore")]
    public async Task<ActionResult<NoteResponse>> RestoreNote(Guid clientId, RestoreNoteRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Title)) return BadRequest(new { error = "Title is required." });
        var userId = GetCurrentUserId();
        var note = await dbContext.Notes.SingleOrDefaultAsync(note => note.UserId == userId && note.ClientId == clientId, cancellationToken);
        if (note is null) return NotFound();
        var conflict = Validate(note, request.BaseUpdatedAt);
        if (conflict is not null) return conflict;
        note.FolderClientId = request.FolderClientId;
        note.Title = request.Title.Trim();
        note.MarkdownBody = request.MarkdownBody ?? string.Empty;
        note.DeletedAt = null;
        note.UpdatedAt = DateTimeOffset.UtcNow;
        await RefreshNoteReferences(note, cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        return ToResponse(note);
    }

    [HttpGet("note-folders")]
    public async Task<IReadOnlyList<NoteFolderResponse>> GetFolders([FromQuery] bool includeDeleted = false, CancellationToken cancellationToken = default)
    {
        var userId = GetCurrentUserId();
        return (await dbContext.NoteFolders
            .Where(folder => folder.UserId == userId && (includeDeleted || folder.DeletedAt == null))
            .OrderBy(folder => folder.Name)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
    }

    [HttpPost("note-folders")]
    public async Task<ActionResult<NoteFolderResponse>> CreateFolder(UpsertFolderRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Name)) return BadRequest(new { error = "Folder name is required." });
        var userId = GetCurrentUserId();
        var clientId = request.ClientId ?? Guid.NewGuid();
        if (request.ParentClientId == clientId || await WouldCreateFolderCycle(userId, clientId, request.ParentClientId, cancellationToken))
        {
            return BadRequest(new { error = "Folder cannot be moved inside itself." });
        }
        var existing = await dbContext.NoteFolders.SingleOrDefaultAsync(folder => folder.UserId == userId && folder.ClientId == clientId, cancellationToken);
        if (existing is not null)
        {
            if (existing.DeletedAt is not null) return FolderConflict(existing, "folder_deleted", "Folder is deleted. Restore it before updating.");
            if (Matches(existing, request)) return Ok(ToResponse(existing));
            var conflict = Validate(existing, request.BaseUpdatedAt);
            if (conflict is not null) return conflict;
            existing.ParentClientId = request.ParentClientId;
            existing.Name = request.Name.Trim();
            existing.UpdatedAt = DateTimeOffset.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
            return Ok(ToResponse(existing));
        }

        var now = DateTimeOffset.UtcNow;
        var folder = new NoteFolder
        {
            UserId = userId,
            ClientId = clientId,
            ParentClientId = request.ParentClientId,
            Name = request.Name.Trim(),
            CreatedAt = now,
            UpdatedAt = now
        };
        dbContext.NoteFolders.Add(folder);
        await dbContext.SaveChangesAsync(cancellationToken);
        return CreatedAtAction(nameof(GetFolders), new { id = folder.Id }, ToResponse(folder));
    }

    [HttpPatch("note-folders/{id:guid}")]
    public async Task<ActionResult<NoteFolderResponse>> UpdateFolder(Guid id, UpdateFolderRequest request, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var folder = await dbContext.NoteFolders.SingleOrDefaultAsync(folder => folder.UserId == userId && folder.Id == id, cancellationToken);
        if (folder is null) return NotFound();
        if (folder.DeletedAt is not null) return FolderConflict(folder, "folder_deleted", "Folder is deleted. Restore it before updating.");
        var conflict = Validate(folder, request.BaseUpdatedAt);
        if (conflict is not null) return conflict;
        if (request.ParentClientId == folder.ClientId || await WouldCreateFolderCycle(userId, folder.ClientId, request.ParentClientId, cancellationToken))
        {
            return BadRequest(new { error = "Folder cannot be moved inside itself." });
        }
        folder.ParentClientId = request.ParentClientId;
        if (request.Name is not null)
        {
            if (string.IsNullOrWhiteSpace(request.Name)) return BadRequest(new { error = "Folder name cannot be blank." });
            folder.Name = request.Name.Trim();
        }
        folder.UpdatedAt = DateTimeOffset.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        return ToResponse(folder);
    }

    [HttpDelete("note-folders/{id:guid}")]
    public async Task<ActionResult<NoteFolderResponse>> DeleteFolder(Guid id, [FromQuery] DateTimeOffset? baseUpdatedAt, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var folder = await dbContext.NoteFolders.SingleOrDefaultAsync(folder => folder.UserId == userId && folder.Id == id, cancellationToken);
        if (folder is null) return NotFound();
        var conflict = Validate(folder, baseUpdatedAt);
        if (conflict is not null) return conflict;
        if (folder.DeletedAt is null)
        {
            var now = DateTimeOffset.UtcNow;
            folder.DeletedAt = now;
            folder.UpdatedAt = now;
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        return ToResponse(folder);
    }

    [HttpGet("tags")]
    public async Task<IReadOnlyList<TagResponse>> GetTags([FromQuery] bool includeDeleted = false, CancellationToken cancellationToken = default)
    {
        var userId = GetCurrentUserId();
        return (await dbContext.Tags
            .Where(tag => tag.UserId == userId && (includeDeleted || tag.DeletedAt == null))
            .OrderBy(tag => tag.Name)
            .ToListAsync(cancellationToken))
            .Select(ToResponse)
            .ToList();
    }

    [HttpPost("tags")]
    public async Task<ActionResult<TagResponse>> CreateTag(UpsertTagRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Name)) return BadRequest(new { error = "Tag name is required." });
        var userId = GetCurrentUserId();
        var clientId = request.ClientId ?? Guid.NewGuid();
        var normalized = NormalizeTag(request.Name);
        var existing = await dbContext.Tags.SingleOrDefaultAsync(tag => tag.UserId == userId && (tag.ClientId == clientId || tag.NormalizedName == normalized), cancellationToken);
        if (existing is not null)
        {
            if (existing.DeletedAt is not null) return TagConflict(existing, "tag_deleted", "Tag is deleted. Restore it before updating.");
            if (existing.Name == request.Name.Trim()) return Ok(ToResponse(existing));
            var conflict = Validate(existing, request.BaseUpdatedAt);
            if (conflict is not null) return conflict;
            existing.Name = request.Name.Trim();
            existing.NormalizedName = normalized;
            existing.UpdatedAt = DateTimeOffset.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
            return Ok(ToResponse(existing));
        }

        var now = DateTimeOffset.UtcNow;
        var tag = new TagItem
        {
            UserId = userId,
            ClientId = clientId,
            Name = request.Name.Trim(),
            NormalizedName = normalized,
            CreatedAt = now,
            UpdatedAt = now
        };
        dbContext.Tags.Add(tag);
        await dbContext.SaveChangesAsync(cancellationToken);
        return CreatedAtAction(nameof(GetTags), new { id = tag.Id }, ToResponse(tag));
    }

    [HttpPatch("tags/{id:guid}")]
    public async Task<ActionResult<TagResponse>> UpdateTag(Guid id, UpdateTagRequest request, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var tag = await dbContext.Tags.SingleOrDefaultAsync(tag => tag.UserId == userId && tag.Id == id, cancellationToken);
        if (tag is null) return NotFound();
        if (tag.DeletedAt is not null) return TagConflict(tag, "tag_deleted", "Tag is deleted. Restore it before updating.");
        var conflict = Validate(tag, request.BaseUpdatedAt);
        if (conflict is not null) return conflict;
        if (request.Name is not null)
        {
            if (string.IsNullOrWhiteSpace(request.Name)) return BadRequest(new { error = "Tag name cannot be blank." });
            tag.Name = request.Name.Trim();
            tag.NormalizedName = NormalizeTag(request.Name);
        }
        tag.UpdatedAt = DateTimeOffset.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        return ToResponse(tag);
    }

    [HttpPost("note-tag-links")]
    public Task<ActionResult<NoteTagLinkResponse>> CreateNoteTagLink(UpsertNoteTagLinkRequest request, CancellationToken cancellationToken)
    {
        return UpsertNoteTagLink(request, cancellationToken);
    }

    [HttpDelete("note-tag-links/{id:guid}")]
    public async Task<ActionResult<NoteTagLinkResponse>> DeleteNoteTagLink(Guid id, [FromQuery] DateTimeOffset? baseUpdatedAt, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var link = await dbContext.NoteTagLinks.SingleOrDefaultAsync(link => link.UserId == userId && link.Id == id, cancellationToken);
        if (link is null) return NotFound();
        var conflict = Validate(link, baseUpdatedAt);
        if (conflict is not null) return conflict;
        if (link.DeletedAt is null)
        {
            var now = DateTimeOffset.UtcNow;
            link.DeletedAt = now;
            link.UpdatedAt = now;
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        return ToResponse(link);
    }

    [HttpPost("task-tag-links")]
    public Task<ActionResult<TaskTagLinkResponse>> CreateTaskTagLink(UpsertTaskTagLinkRequest request, CancellationToken cancellationToken)
    {
        return UpsertTaskTagLink(request, cancellationToken);
    }

    [HttpDelete("task-tag-links/{id:guid}")]
    public async Task<ActionResult<TaskTagLinkResponse>> DeleteTaskTagLink(Guid id, [FromQuery] DateTimeOffset? baseUpdatedAt, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var link = await dbContext.TaskTagLinks.SingleOrDefaultAsync(link => link.UserId == userId && link.Id == id, cancellationToken);
        if (link is null) return NotFound();
        var conflict = Validate(link, baseUpdatedAt);
        if (conflict is not null) return conflict;
        if (link.DeletedAt is null)
        {
            var now = DateTimeOffset.UtcNow;
            link.DeletedAt = now;
            link.UpdatedAt = now;
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        return ToResponse(link);
    }

    private async Task<ActionResult<NoteTagLinkResponse>> UpsertNoteTagLink(UpsertNoteTagLinkRequest request, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var clientId = request.ClientId ?? Guid.NewGuid();
        var existing = await dbContext.NoteTagLinks.SingleOrDefaultAsync(
            link => link.UserId == userId && (link.ClientId == clientId || (link.NoteClientId == request.NoteClientId && link.TagClientId == request.TagClientId)),
            cancellationToken);
        if (existing is not null)
        {
            if (existing.NoteClientId == request.NoteClientId && existing.TagClientId == request.TagClientId) return Ok(ToResponse(existing));
            var conflict = Validate(existing, request.BaseUpdatedAt);
            if (conflict is not null) return conflict;
        }
        var now = DateTimeOffset.UtcNow;
        var link = new NoteTagLink
        {
            UserId = userId,
            ClientId = clientId,
            NoteClientId = request.NoteClientId,
            TagClientId = request.TagClientId,
            CreatedAt = now,
            UpdatedAt = now
        };
        dbContext.NoteTagLinks.Add(link);
        await dbContext.SaveChangesAsync(cancellationToken);
        return CreatedAtAction(nameof(GetSync), new { id = link.Id }, ToResponse(link));
    }

    private async Task<ActionResult<TaskTagLinkResponse>> UpsertTaskTagLink(UpsertTaskTagLinkRequest request, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var clientId = request.ClientId ?? Guid.NewGuid();
        var existing = await dbContext.TaskTagLinks.SingleOrDefaultAsync(
            link => link.UserId == userId && (link.ClientId == clientId || (link.TaskClientId == request.TaskClientId && link.TagClientId == request.TagClientId)),
            cancellationToken);
        if (existing is not null)
        {
            if (existing.TaskClientId == request.TaskClientId && existing.TagClientId == request.TagClientId) return Ok(ToResponse(existing));
            var conflict = Validate(existing, request.BaseUpdatedAt);
            if (conflict is not null) return conflict;
        }
        var now = DateTimeOffset.UtcNow;
        var link = new TaskTagLink
        {
            UserId = userId,
            ClientId = clientId,
            TaskClientId = request.TaskClientId,
            TagClientId = request.TagClientId,
            CreatedAt = now,
            UpdatedAt = now
        };
        dbContext.TaskTagLinks.Add(link);
        await dbContext.SaveChangesAsync(cancellationToken);
        return CreatedAtAction(nameof(GetSync), new { id = link.Id }, ToResponse(link));
    }

    private async Task RefreshNoteReferences(NoteItem note, CancellationToken cancellationToken)
    {
        var oldTaskReferences = dbContext.NoteTaskReferences.Where(reference => reference.UserId == note.UserId && reference.NoteClientId == note.ClientId);
        var oldNoteReferences = dbContext.NoteNoteReferences.Where(reference => reference.UserId == note.UserId && reference.SourceNoteClientId == note.ClientId);
        dbContext.NoteTaskReferences.RemoveRange(oldTaskReferences);
        dbContext.NoteNoteReferences.RemoveRange(oldNoteReferences);
        if (note.DeletedAt is not null) return;

        var taskIds = ExtractWikiIds(note.MarkdownBody, "task");
        var noteIds = ExtractWikiIds(note.MarkdownBody, "note").Where(id => id != note.ClientId).ToHashSet();
        foreach (var taskId in taskIds)
        {
            dbContext.NoteTaskReferences.Add(new NoteTaskReference { UserId = note.UserId, NoteClientId = note.ClientId, TaskClientId = taskId });
        }
        foreach (var noteId in noteIds)
        {
            dbContext.NoteNoteReferences.Add(new NoteNoteReference { UserId = note.UserId, SourceNoteClientId = note.ClientId, TargetNoteClientId = noteId });
        }
        await Task.CompletedTask;
    }

    private async Task<bool> WouldCreateFolderCycle(Guid userId, Guid folderClientId, Guid? newParentClientId, CancellationToken cancellationToken)
    {
        var seen = new HashSet<Guid> { folderClientId };
        var parentId = newParentClientId;
        while (parentId is not null)
        {
            if (!seen.Add(parentId.Value)) return true;
            var parent = await dbContext.NoteFolders
                .AsNoTracking()
                .SingleOrDefaultAsync(folder => folder.UserId == userId && folder.ClientId == parentId.Value, cancellationToken);
            parentId = parent?.ParentClientId;
        }
        return false;
    }

    private Guid GetCurrentUserId()
    {
        var value = User.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? User.FindFirstValue("sub")
            ?? throw new InvalidOperationException("Authenticated user id claim is missing.");
        return Guid.Parse(value);
    }

    private static bool Matches(NoteItem note, UpsertNoteRequest request)
    {
        return note.FolderClientId == request.FolderClientId
            && note.Title == request.Title.Trim()
            && note.MarkdownBody == (request.MarkdownBody ?? string.Empty);
    }

    private static bool Matches(NoteFolder folder, UpsertFolderRequest request)
    {
        return folder.ParentClientId == request.ParentClientId && folder.Name == request.Name.Trim();
    }

    private static void Apply(NoteItem note, UpsertNoteRequest request)
    {
        note.FolderClientId = request.FolderClientId;
        note.Title = request.Title.Trim();
        note.MarkdownBody = request.MarkdownBody ?? string.Empty;
    }

    private ActionResult<NoteResponse>? Validate(NoteItem note, DateTimeOffset? baseUpdatedAt)
    {
        if (baseUpdatedAt is null) return NoteConflict(note, "note_conflict", "baseUpdatedAt is required when modifying an existing note.");
        return SameVersion(note.UpdatedAt, baseUpdatedAt.Value) ? null : NoteConflict(note, "note_conflict", "Note has changed on the server. Refresh before updating.");
    }

    private ActionResult<NoteFolderResponse>? Validate(NoteFolder folder, DateTimeOffset? baseUpdatedAt)
    {
        if (baseUpdatedAt is null) return FolderConflict(folder, "folder_conflict", "baseUpdatedAt is required when modifying an existing folder.");
        return SameVersion(folder.UpdatedAt, baseUpdatedAt.Value) ? null : FolderConflict(folder, "folder_conflict", "Folder has changed on the server. Refresh before updating.");
    }

    private ActionResult<TagResponse>? Validate(TagItem tag, DateTimeOffset? baseUpdatedAt)
    {
        if (baseUpdatedAt is null) return TagConflict(tag, "tag_conflict", "baseUpdatedAt is required when modifying an existing tag.");
        return SameVersion(tag.UpdatedAt, baseUpdatedAt.Value) ? null : TagConflict(tag, "tag_conflict", "Tag has changed on the server. Refresh before updating.");
    }

    private ActionResult<NoteTagLinkResponse>? Validate(NoteTagLink link, DateTimeOffset? baseUpdatedAt)
    {
        if (baseUpdatedAt is null) return NoteTagLinkConflict(link, "note_tag_link_conflict", "baseUpdatedAt is required when modifying an existing note tag link.");
        return SameVersion(link.UpdatedAt, baseUpdatedAt.Value) ? null : NoteTagLinkConflict(link, "note_tag_link_conflict", "Note tag link has changed on the server. Refresh before updating.");
    }

    private ActionResult<TaskTagLinkResponse>? Validate(TaskTagLink link, DateTimeOffset? baseUpdatedAt)
    {
        if (baseUpdatedAt is null) return TaskTagLinkConflict(link, "task_tag_link_conflict", "baseUpdatedAt is required when modifying an existing task tag link.");
        return SameVersion(link.UpdatedAt, baseUpdatedAt.Value) ? null : TaskTagLinkConflict(link, "task_tag_link_conflict", "Task tag link has changed on the server. Refresh before updating.");
    }

    private ActionResult<NoteResponse> NoteConflict(NoteItem note, string code, string error) => Conflict(new NoteConflictResponse(code, error, ToResponse(note)));
    private ActionResult<NoteFolderResponse> FolderConflict(NoteFolder folder, string code, string error) => Conflict(new FolderConflictResponse(code, error, ToResponse(folder)));
    private ActionResult<TagResponse> TagConflict(TagItem tag, string code, string error) => Conflict(new TagConflictResponse(code, error, ToResponse(tag)));
    private ActionResult<NoteTagLinkResponse> NoteTagLinkConflict(NoteTagLink link, string code, string error) => Conflict(new NoteTagLinkConflictResponse(code, error, ToResponse(link)));
    private ActionResult<TaskTagLinkResponse> TaskTagLinkConflict(TaskTagLink link, string code, string error) => Conflict(new TaskTagLinkConflictResponse(code, error, ToResponse(link)));

    private static NoteResponse ToResponse(NoteItem note) => new(note.Id, note.ClientId, note.FolderClientId, note.Title, note.MarkdownBody, note.CreatedAt, note.UpdatedAt, note.DeletedAt);
    private static NoteFolderResponse ToResponse(NoteFolder folder) => new(folder.Id, folder.ClientId, folder.ParentClientId, folder.Name, folder.CreatedAt, folder.UpdatedAt, folder.DeletedAt);
    private static TagResponse ToResponse(TagItem tag) => new(tag.Id, tag.ClientId, tag.Name, tag.NormalizedName, tag.CreatedAt, tag.UpdatedAt, tag.DeletedAt);
    private static NoteTagLinkResponse ToResponse(NoteTagLink link) => new(link.Id, link.ClientId, link.NoteClientId, link.TagClientId, link.CreatedAt, link.UpdatedAt, link.DeletedAt);
    private static TaskTagLinkResponse ToResponse(TaskTagLink link) => new(link.Id, link.ClientId, link.TaskClientId, link.TagClientId, link.CreatedAt, link.UpdatedAt, link.DeletedAt);

    private static string NormalizeTag(string name) => name.Trim().ToUpperInvariant();
    private static bool SameVersion(DateTimeOffset left, DateTimeOffset right) => left.ToUnixTimeMilliseconds() == right.ToUnixTimeMilliseconds();

    private static HashSet<Guid> ExtractWikiIds(string markdown, string type)
    {
        return WikiLinkRegex().Matches(markdown)
            .Select(match => match.Groups["type"].Value == type && Guid.TryParse(match.Groups["id"].Value, out var id) ? id : (Guid?)null)
            .Where(id => id is not null)
            .Select(id => id!.Value)
            .ToHashSet();
    }

    [GeneratedRegex(@"\[\[(?<type>task|note):(?<id>[0-9a-fA-F-]{36})(?:\|[^\]]+)?\]\]")]
    private static partial Regex WikiLinkRegex();
}
