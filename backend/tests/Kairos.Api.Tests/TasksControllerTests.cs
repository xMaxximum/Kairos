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

public sealed class TasksControllerTests
{
    [Fact]
    public async Task StaleUpdateReturnsTaskConflictWithServerTask()
    {
        var userId = Guid.NewGuid();
        await using var db = CreateDbContext();
        var task = SeedTask(db, userId, updatedAt: DateTimeOffset.Parse("2026-05-15T00:00:00Z"));
        task.Title = "Server title";
        task.UpdatedAt = DateTimeOffset.Parse("2026-05-15T00:05:00Z");
        await db.SaveChangesAsync();

        var controller = CreateController(db, userId);
        var result = await controller.UpdateTask(
            task.Id,
            new UpdateTaskRequest(
                Title: "Local title",
                Description: null,
                ReminderTime: null,
                Recurrence: null,
                IsHighPriority: null,
                IsFullScreenReminder: null,
                Attachments: null,
                IsCompleted: null,
                IsArchived: null,
                IsOneOffTask: null,
                BaseUpdatedAt: DateTimeOffset.Parse("2026-05-15T00:00:00Z")),
            CancellationToken.None);

        var conflict = AssertConflict(result);
        Assert.Equal("task_conflict", conflict.Code);
        Assert.Equal("Server title", conflict.ServerTask.Title);
    }

    [Fact]
    public async Task StaleDeleteReturnsTaskConflictWithServerTask()
    {
        var userId = Guid.NewGuid();
        await using var db = CreateDbContext();
        var task = SeedTask(db, userId, updatedAt: DateTimeOffset.Parse("2026-05-15T00:00:00Z"));
        task.Description = "Server edit";
        task.UpdatedAt = DateTimeOffset.Parse("2026-05-15T00:10:00Z");
        await db.SaveChangesAsync();

        var controller = CreateController(db, userId);
        var result = await controller.DeleteTask(
            task.Id,
            DateTimeOffset.Parse("2026-05-15T00:00:00Z"),
            CancellationToken.None);

        var conflict = AssertConflict(result);
        Assert.Equal("task_conflict", conflict.Code);
        Assert.Equal("Server edit", conflict.ServerTask.Description);
    }

    [Fact]
    public async Task StaleRestoreReturnsTaskConflictWithServerTask()
    {
        var userId = Guid.NewGuid();
        await using var db = CreateDbContext();
        var task = SeedTask(
            db,
            userId,
            updatedAt: DateTimeOffset.Parse("2026-05-15T00:10:00Z"),
            deletedAt: DateTimeOffset.Parse("2026-05-15T00:10:00Z"));

        var controller = CreateController(db, userId);
        var result = await controller.RestoreTaskByClientId(
            task.ClientId,
            RestoreRequest(baseUpdatedAt: DateTimeOffset.Parse("2026-05-15T00:00:00Z")),
            CancellationToken.None);

        var conflict = AssertConflict(result);
        Assert.Equal("task_conflict", conflict.Code);
        Assert.NotNull(conflict.ServerTask.DeletedAt);
    }

    [Fact]
    public async Task UpdateDeletedTaskReturnsDeletedConflictWithServerTask()
    {
        var userId = Guid.NewGuid();
        await using var db = CreateDbContext();
        var task = SeedTask(
            db,
            userId,
            updatedAt: DateTimeOffset.Parse("2026-05-15T00:00:00Z"),
            deletedAt: DateTimeOffset.Parse("2026-05-15T00:00:00Z"));

        var controller = CreateController(db, userId);
        var result = await controller.UpdateTask(
            task.Id,
            new UpdateTaskRequest(
                Title: "Local title",
                Description: null,
                ReminderTime: null,
                Recurrence: null,
                IsHighPriority: null,
                IsFullScreenReminder: null,
                Attachments: null,
                IsCompleted: null,
                IsArchived: null,
                IsOneOffTask: null,
                BaseUpdatedAt: task.UpdatedAt),
            CancellationToken.None);

        var conflict = AssertConflict(result);
        Assert.Equal("task_deleted", conflict.Code);
        Assert.NotNull(conflict.ServerTask.DeletedAt);
    }

    [Fact]
    public async Task IdempotentCreateWithSameClientIdReturnsExistingTask()
    {
        var userId = Guid.NewGuid();
        var clientId = Guid.NewGuid();
        await using var db = CreateDbContext();
        var controller = CreateController(db, userId);
        var request = CreateRequest(clientId);

        var created = await controller.CreateTask(request, CancellationToken.None);
        var createdTask = Assert.IsType<TaskResponse>(
            Assert.IsType<CreatedAtActionResult>(created.Result).Value);

        var repeated = await controller.CreateTask(request, CancellationToken.None);
        var repeatedTask = Assert.IsType<TaskResponse>(
            Assert.IsType<OkObjectResult>(repeated.Result).Value);

        Assert.Equal(createdTask.Id, repeatedTask.Id);
        Assert.Equal(createdTask.UpdatedAt, repeatedTask.UpdatedAt);
    }

    private static AppDbContext CreateDbContext()
    {
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase(Guid.NewGuid().ToString())
            .Options;
        return new AppDbContext(options);
    }

    private static TasksController CreateController(AppDbContext db, Guid userId)
    {
        var controller = new TasksController(db);
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

    private static TaskItem SeedTask(
        AppDbContext db,
        Guid userId,
        DateTimeOffset updatedAt,
        DateTimeOffset? deletedAt = null)
    {
        var task = new TaskItem
        {
            UserId = userId,
            ClientId = Guid.NewGuid(),
            Title = "Task",
            Description = "",
            CreatedAt = DateTimeOffset.Parse("2026-05-15T00:00:00Z"),
            UpdatedAt = updatedAt,
            DeletedAt = deletedAt,
            Recurrence = "NONE"
        };
        db.Tasks.Add(task);
        db.SaveChanges();
        return task;
    }

    private static CreateTaskRequest CreateRequest(Guid clientId)
    {
        return new CreateTaskRequest(
            ClientId: clientId,
            Title: "Task",
            Description: "",
            ReminderTime: null,
            Recurrence: "NONE",
            IsHighPriority: false,
            IsFullScreenReminder: false,
            Attachments: [],
            IsCompleted: false,
            IsArchived: false,
            IsOneOffTask: false);
    }

    private static RestoreTaskRequest RestoreRequest(DateTimeOffset baseUpdatedAt)
    {
        return new RestoreTaskRequest(
            Title: "Task",
            Description: "",
            ReminderTime: null,
            Recurrence: "NONE",
            IsHighPriority: false,
            IsFullScreenReminder: false,
            Attachments: [],
            IsCompleted: false,
            IsArchived: false,
            IsOneOffTask: false,
            BaseUpdatedAt: baseUpdatedAt);
    }

    private static TaskConflictResponse AssertConflict(ActionResult<TaskResponse> result)
    {
        var objectResult = Assert.IsType<ConflictObjectResult>(result.Result);
        return Assert.IsType<TaskConflictResponse>(objectResult.Value);
    }
}
