using Kairos.Api.Contracts;
using Kairos.Api.Data;
using Kairos.Api.Data.Entities;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;

namespace Kairos.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/tasks")]
public sealed class TasksController(AppDbContext dbContext) : ControllerBase
{
    [HttpGet]
    public async Task<IReadOnlyList<TaskResponse>> GetTasks(
        [FromQuery] bool includeDeleted = false,
        CancellationToken cancellationToken = default)
    {
        var userId = GetCurrentUserId();
        var query = dbContext.Tasks.Where(task => task.UserId == userId);
        if (!includeDeleted)
        {
            query = query.Where(task => task.DeletedAt == null);
        }

        return await query
            .OrderByDescending(task => task.UpdatedAt)
            .Select(task => new TaskResponse(
                task.Id,
                task.ClientId,
                task.Title,
                task.Description,
                task.CreatedAt,
                task.UpdatedAt,
                task.DeletedAt,
                task.ReminderTime,
                task.Recurrence,
                task.IsHighPriority,
                task.IsFullScreenReminder,
                task.Attachments,
                task.IsCompleted,
                task.IsArchived,
                task.IsOneOffTask))
            .ToListAsync(cancellationToken);
    }

    [HttpGet("changes")]
    public async Task<TaskChangesResponse> GetTaskChanges(
        [FromQuery] DateTimeOffset? since = null,
        [FromQuery] bool includeDeleted = true,
        CancellationToken cancellationToken = default)
    {
        var userId = GetCurrentUserId();
        var query = dbContext.Tasks.Where(task => task.UserId == userId);
        if (since is not null)
        {
            query = query.Where(task => task.UpdatedAt > since.Value);
        }
        if (!includeDeleted)
        {
            query = query.Where(task => task.DeletedAt == null);
        }

        var tasks = await query
            .OrderBy(task => task.UpdatedAt)
            .Select(task => new TaskResponse(
                task.Id,
                task.ClientId,
                task.Title,
                task.Description,
                task.CreatedAt,
                task.UpdatedAt,
                task.DeletedAt,
                task.ReminderTime,
                task.Recurrence,
                task.IsHighPriority,
                task.IsFullScreenReminder,
                task.Attachments,
                task.IsCompleted,
                task.IsArchived,
                task.IsOneOffTask))
            .ToListAsync(cancellationToken);

        var cursor = tasks.Count == 0 ? since ?? DateTimeOffset.UtcNow : tasks[^1].UpdatedAt;
        return new TaskChangesResponse(tasks, cursor);
    }

    [HttpPost]
    public async Task<ActionResult<TaskResponse>> CreateTask(CreateTaskRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Title))
        {
            return BadRequest(new { error = "Title is required." });
        }

        var userId = GetCurrentUserId();
        var clientId = request.ClientId ?? Guid.NewGuid();
        var existing = await dbContext.Tasks.SingleOrDefaultAsync(
            task => task.UserId == userId && task.ClientId == clientId,
            cancellationToken);
        if (existing is not null)
        {
            if (existing.DeletedAt is not null)
            {
                return TaskConflict(
                    existing,
                    "task_deleted",
                    "Task is deleted. Restore it before updating.");
            }
            var matchesExisting = MatchesRequest(existing, request);
            if (matchesExisting)
            {
                return Ok(ToResponse(existing));
            }

            if (!matchesExisting)
            {
                var conflict = ValidateBaseUpdatedAt(existing, request.BaseUpdatedAt);
                if (conflict is not null) return conflict;
            }

            ApplyRequest(existing, request);
            existing.UpdatedAt = DateTimeOffset.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
            return Ok(ToResponse(existing));
        }

        var now = DateTimeOffset.UtcNow;
        var task = new TaskItem
        {
            UserId = userId,
            ClientId = clientId,
            Title = request.Title.Trim(),
            Description = request.Description?.Trim() ?? string.Empty,
            CreatedAt = now,
            UpdatedAt = now,
            ReminderTime = request.ReminderTime,
            Recurrence = NormalizeRecurrence(request.Recurrence),
            IsHighPriority = request.IsHighPriority,
            IsFullScreenReminder = request.IsFullScreenReminder,
            Attachments = request.Attachments?.ToList() ?? [],
            IsCompleted = request.IsCompleted,
            IsArchived = request.IsArchived,
            IsOneOffTask = request.IsOneOffTask
        };

        dbContext.Tasks.Add(task);
        await dbContext.SaveChangesAsync(cancellationToken);
        return CreatedAtAction(nameof(GetTask), new { id = task.Id }, ToResponse(task));
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<TaskResponse>> GetTask(Guid id, CancellationToken cancellationToken)
    {
        var task = await FindUserTask(id, cancellationToken);
        return task is null ? NotFound() : ToResponse(task);
    }

    [HttpPatch("{id:guid}")]
    public async Task<ActionResult<TaskResponse>> UpdateTask(
        Guid id,
        UpdateTaskRequest request,
        CancellationToken cancellationToken)
    {
        var task = await FindUserTaskIncludingDeleted(id, cancellationToken);
        if (task is null)
        {
            return NotFound();
        }
        if (task.DeletedAt is not null)
        {
            return TaskConflict(
                task,
                "task_deleted",
                "Task is deleted. Restore it before updating.");
        }

        var conflict = ValidateBaseUpdatedAt(task, request.BaseUpdatedAt);
        if (conflict is not null) return conflict;

        if (request.Title is not null)
        {
            if (string.IsNullOrWhiteSpace(request.Title))
            {
                return BadRequest(new { error = "Title cannot be blank." });
            }
            task.Title = request.Title.Trim();
        }
        if (request.Description is not null) task.Description = request.Description.Trim();
        if (request.ReminderTime.HasValue) task.ReminderTime = request.ReminderTime;
        if (request.Recurrence is not null) task.Recurrence = NormalizeRecurrence(request.Recurrence);
        if (request.IsHighPriority.HasValue) task.IsHighPriority = request.IsHighPriority.Value;
        if (request.IsFullScreenReminder.HasValue) task.IsFullScreenReminder = request.IsFullScreenReminder.Value;
        if (request.Attachments is not null) task.Attachments = request.Attachments.ToList();
        if (request.IsCompleted.HasValue) task.IsCompleted = request.IsCompleted.Value;
        if (request.IsArchived.HasValue) task.IsArchived = request.IsArchived.Value;
        if (request.IsOneOffTask.HasValue) task.IsOneOffTask = request.IsOneOffTask.Value;
        task.UpdatedAt = DateTimeOffset.UtcNow;

        await dbContext.SaveChangesAsync(cancellationToken);
        return ToResponse(task);
    }

    [HttpDelete("{id:guid}")]
    public async Task<ActionResult<TaskResponse>> DeleteTask(
        Guid id,
        [FromQuery] DateTimeOffset? baseUpdatedAt,
        CancellationToken cancellationToken)
    {
        var task = await FindUserTaskIncludingDeleted(id, cancellationToken);
        if (task is null)
        {
            return NotFound();
        }

        var conflict = ValidateBaseUpdatedAt(task, baseUpdatedAt);
        if (conflict is not null) return conflict;

        if (task.DeletedAt is not null)
        {
            return Ok(ToResponse(task));
        }

        var now = DateTimeOffset.UtcNow;
        task.DeletedAt = now;
        task.UpdatedAt = now;
        await dbContext.SaveChangesAsync(cancellationToken);
        return Ok(ToResponse(task));
    }

    [HttpDelete("client/{clientId:guid}")]
    public async Task<ActionResult<TaskResponse>> DeleteTaskByClientId(
        Guid clientId,
        [FromQuery] DateTimeOffset? baseUpdatedAt,
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        var task = await dbContext.Tasks.SingleOrDefaultAsync(
            task => task.UserId == userId && task.ClientId == clientId,
            cancellationToken);
        if (task is null)
        {
            return NotFound();
        }

        var conflict = ValidateBaseUpdatedAt(task, baseUpdatedAt);
        if (conflict is not null) return conflict;

        if (task.DeletedAt is not null)
        {
            return Ok(ToResponse(task));
        }

        var now = DateTimeOffset.UtcNow;
        task.DeletedAt = now;
        task.UpdatedAt = now;
        await dbContext.SaveChangesAsync(cancellationToken);
        return Ok(ToResponse(task));
    }

    [HttpPost("client/{clientId:guid}/restore")]
    public async Task<ActionResult<TaskResponse>> RestoreTaskByClientId(
        Guid clientId,
        RestoreTaskRequest request,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Title))
        {
            return BadRequest(new { error = "Title is required." });
        }

        var userId = GetCurrentUserId();
        var task = await dbContext.Tasks.SingleOrDefaultAsync(
            task => task.UserId == userId && task.ClientId == clientId,
            cancellationToken);
        if (task is null)
        {
            return NotFound();
        }

        var conflict = ValidateBaseUpdatedAt(task, request.BaseUpdatedAt);
        if (conflict is not null) return conflict;

        ApplyRestoreRequest(task, request);
        task.DeletedAt = null;
        task.UpdatedAt = DateTimeOffset.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        return Ok(ToResponse(task));
    }

    private Task<TaskItem?> FindUserTask(Guid id, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        return dbContext.Tasks.SingleOrDefaultAsync(
            task => task.Id == id && task.UserId == userId && task.DeletedAt == null,
            cancellationToken);
    }

    private Task<TaskItem?> FindUserTaskIncludingDeleted(Guid id, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        return dbContext.Tasks.SingleOrDefaultAsync(
            task => task.Id == id && task.UserId == userId,
            cancellationToken);
    }

    private Guid GetCurrentUserId()
    {
        var value = User.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? User.FindFirstValue("sub")
            ?? throw new InvalidOperationException("Authenticated user id claim is missing.");
        return Guid.Parse(value);
    }

    private static string NormalizeRecurrence(string? recurrence)
    {
        var normalized = string.IsNullOrWhiteSpace(recurrence) ? "NONE" : recurrence.Trim().ToUpperInvariant();
        return normalized is "NONE" or "DAILY" or "WEEKLY" ? normalized : "NONE";
    }

    private static void ApplyRequest(TaskItem task, CreateTaskRequest request)
    {
        task.Title = request.Title.Trim();
        task.Description = request.Description?.Trim() ?? string.Empty;
        task.ReminderTime = request.ReminderTime;
        task.Recurrence = NormalizeRecurrence(request.Recurrence);
        task.IsHighPriority = request.IsHighPriority;
        task.IsFullScreenReminder = request.IsFullScreenReminder;
        task.Attachments = request.Attachments?.ToList() ?? [];
        task.IsCompleted = request.IsCompleted;
        task.IsArchived = request.IsArchived;
        task.IsOneOffTask = request.IsOneOffTask;
    }

    private static void ApplyRestoreRequest(TaskItem task, RestoreTaskRequest request)
    {
        task.Title = request.Title.Trim();
        task.Description = request.Description?.Trim() ?? string.Empty;
        task.ReminderTime = request.ReminderTime;
        task.Recurrence = NormalizeRecurrence(request.Recurrence);
        task.IsHighPriority = request.IsHighPriority;
        task.IsFullScreenReminder = request.IsFullScreenReminder;
        task.Attachments = request.Attachments?.ToList() ?? [];
        task.IsCompleted = request.IsCompleted;
        task.IsArchived = request.IsArchived;
        task.IsOneOffTask = request.IsOneOffTask;
    }

    private static bool MatchesRequest(TaskItem task, CreateTaskRequest request)
    {
        return task.Title == request.Title.Trim()
            && task.Description == (request.Description?.Trim() ?? string.Empty)
            && SameInstant(task.ReminderTime, request.ReminderTime)
            && task.Recurrence == NormalizeRecurrence(request.Recurrence)
            && task.IsHighPriority == request.IsHighPriority
            && task.IsFullScreenReminder == request.IsFullScreenReminder
            && task.Attachments.SequenceEqual(request.Attachments ?? [])
            && task.IsCompleted == request.IsCompleted
            && task.IsArchived == request.IsArchived
            && task.IsOneOffTask == request.IsOneOffTask;
    }

    private ActionResult<TaskResponse>? ValidateBaseUpdatedAt(TaskItem task, DateTimeOffset? baseUpdatedAt)
    {
        if (baseUpdatedAt is null)
        {
            return TaskConflict(
                task,
                "task_conflict",
                "baseUpdatedAt is required when modifying an existing task.");
        }

        if (task.UpdatedAt.ToUnixTimeMilliseconds() != baseUpdatedAt.Value.ToUnixTimeMilliseconds())
        {
            return TaskConflict(
                task,
                "task_conflict",
                "Task has changed on the server. Refresh before updating.");
        }

        return null;
    }

    private ActionResult<TaskResponse> TaskConflict(TaskItem task, string code, string error)
    {
        return Conflict(new TaskConflictResponse(code, error, ToResponse(task)));
    }

    private static bool SameInstant(DateTimeOffset? left, DateTimeOffset? right)
    {
        if (left is null || right is null) return left is null && right is null;
        return left.Value.ToUnixTimeMilliseconds() == right.Value.ToUnixTimeMilliseconds();
    }

    private static TaskResponse ToResponse(TaskItem task)
    {
        return new TaskResponse(
            task.Id,
            task.ClientId,
            task.Title,
            task.Description,
            task.CreatedAt,
            task.UpdatedAt,
            task.DeletedAt,
            task.ReminderTime,
            task.Recurrence,
            task.IsHighPriority,
            task.IsFullScreenReminder,
            task.Attachments,
            task.IsCompleted,
            task.IsArchived,
            task.IsOneOffTask);
    }
}
