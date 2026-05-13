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
    public async Task<IReadOnlyList<TaskResponse>> GetTasks(CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        return await dbContext.Tasks
            .Where(task => task.UserId == userId && task.DeletedAt == null)
            .OrderByDescending(task => task.UpdatedAt)
            .Select(task => new TaskResponse(
                task.Id,
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

    [HttpPost]
    public async Task<ActionResult<TaskResponse>> CreateTask(CreateTaskRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Title))
        {
            return BadRequest(new { error = "Title is required." });
        }

        var now = DateTimeOffset.UtcNow;
        var task = new TaskItem
        {
            UserId = GetCurrentUserId(),
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
        var task = await FindUserTask(id, cancellationToken);
        if (task is null)
        {
            return NotFound();
        }

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
    public async Task<IActionResult> DeleteTask(Guid id, CancellationToken cancellationToken)
    {
        var task = await FindUserTask(id, cancellationToken);
        if (task is null)
        {
            return NotFound();
        }

        var now = DateTimeOffset.UtcNow;
        task.DeletedAt = now;
        task.UpdatedAt = now;
        await dbContext.SaveChangesAsync(cancellationToken);
        return NoContent();
    }

    private Task<TaskItem?> FindUserTask(Guid id, CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();
        return dbContext.Tasks.SingleOrDefaultAsync(
            task => task.Id == id && task.UserId == userId && task.DeletedAt == null,
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

    private static TaskResponse ToResponse(TaskItem task)
    {
        return new TaskResponse(
            task.Id,
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
