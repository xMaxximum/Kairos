namespace Kairos.Api.Contracts;

public sealed record TaskResponse(
    Guid Id,
    Guid ClientId,
    string Title,
    string Description,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt,
    DateTimeOffset? ReminderTime,
    string Recurrence,
    bool IsHighPriority,
    bool IsFullScreenReminder,
    IReadOnlyList<string> Attachments,
    bool IsCompleted,
    bool IsArchived,
    bool IsOneOffTask);

public sealed record CreateTaskRequest(
    Guid? ClientId,
    string Title,
    string? Description,
    DateTimeOffset? ReminderTime,
    string? Recurrence,
    bool IsHighPriority,
    bool IsFullScreenReminder,
    IReadOnlyList<string>? Attachments,
    bool IsCompleted,
    bool IsArchived,
    bool IsOneOffTask,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record UpdateTaskRequest(
    string? Title,
    string? Description,
    DateTimeOffset? ReminderTime,
    string? Recurrence,
    bool? IsHighPriority,
    bool? IsFullScreenReminder,
    IReadOnlyList<string>? Attachments,
    bool? IsCompleted,
    bool? IsArchived,
    bool? IsOneOffTask,
    DateTimeOffset? BaseUpdatedAt = null);

public sealed record RestoreTaskRequest(
    string Title,
    string? Description,
    DateTimeOffset? ReminderTime,
    string? Recurrence,
    bool IsHighPriority,
    bool IsFullScreenReminder,
    IReadOnlyList<string>? Attachments,
    bool IsCompleted,
    bool IsArchived,
    bool IsOneOffTask,
    DateTimeOffset? BaseUpdatedAt);

public sealed record TaskConflictResponse(
    string Code,
    string Error,
    TaskResponse ServerTask);
