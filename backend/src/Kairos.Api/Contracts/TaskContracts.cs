namespace Kairos.Api.Contracts;

public sealed record TaskResponse(
    Guid Id,
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
    string Title,
    string? Description,
    DateTimeOffset? ReminderTime,
    string? Recurrence,
    bool IsHighPriority,
    bool IsFullScreenReminder,
    IReadOnlyList<string>? Attachments,
    bool IsCompleted,
    bool IsArchived,
    bool IsOneOffTask);

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
    bool? IsOneOffTask);
