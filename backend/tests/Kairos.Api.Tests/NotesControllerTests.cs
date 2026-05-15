using System.Security.Claims;
using Kairos.Api.Contracts;
using Kairos.Api.Controllers;
using Kairos.Api.Data;
using Kairos.Api.Data.Entities;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Kairos.Api.Tests;

public sealed class NotesControllerTests
{
    [Fact]
    public async Task CreateNoteParsesTaskAndNoteWikiReferences()
    {
        var userId = Guid.NewGuid();
        var taskClientId = Guid.NewGuid();
        var targetNoteClientId = Guid.NewGuid();
        await using var db = CreateDbContext();
        db.Tasks.Add(new TaskItem
        {
            UserId = userId,
            ClientId = taskClientId,
            Title = "Task",
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.UtcNow
        });
        db.Notes.Add(new NoteItem
        {
            UserId = userId,
            ClientId = targetNoteClientId,
            Title = "Target",
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.UtcNow
        });
        await db.SaveChangesAsync();

        var controller = CreateController(db, userId);
        var result = await controller.CreateNote(
            new UpsertNoteRequest(
                ClientId: Guid.NewGuid(),
                FolderClientId: null,
                Title: "Source",
                MarkdownBody: $"See [[task:{taskClientId}|Task]] and [[note:{targetNoteClientId}|Target]]."),
            CancellationToken.None);

        Assert.IsType<CreatedAtActionResult>(result.Result);
        var sync = await controller.GetSync(cancellationToken: CancellationToken.None);

        Assert.Contains(sync.NoteTaskReferences, reference => reference.TaskClientId == taskClientId);
        Assert.Contains(sync.NoteNoteReferences, reference => reference.TargetNoteClientId == targetNoteClientId);
    }

    [Fact]
    public async Task FolderMoveRejectsCycles()
    {
        var userId = Guid.NewGuid();
        await using var db = CreateDbContext();
        var parent = new NoteFolder
        {
            UserId = userId,
            ClientId = Guid.NewGuid(),
            Name = "Parent",
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.Parse("2026-05-15T00:00:00Z")
        };
        var child = new NoteFolder
        {
            UserId = userId,
            ClientId = Guid.NewGuid(),
            ParentClientId = parent.ClientId,
            Name = "Child",
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.UtcNow
        };
        db.NoteFolders.AddRange(parent, child);
        await db.SaveChangesAsync();

        var controller = CreateController(db, userId);
        var result = await controller.UpdateFolder(
            parent.Id,
            new UpdateFolderRequest(
                ParentClientId: child.ClientId,
                Name: null,
                BaseUpdatedAt: parent.UpdatedAt),
            CancellationToken.None);

        Assert.IsType<BadRequestObjectResult>(result.Result);
    }

    private static AppDbContext CreateDbContext()
    {
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase(Guid.NewGuid().ToString())
            .Options;
        return new AppDbContext(options);
    }

    private static NotesController CreateController(AppDbContext db, Guid userId)
    {
        var controller = new NotesController(db);
        controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext
            {
                User = new ClaimsPrincipal(new ClaimsIdentity(
                    [new Claim(ClaimTypes.NameIdentifier, userId.ToString())],
                    "Test"))
            }
        };
        return controller;
    }
}
