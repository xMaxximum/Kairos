namespace Kairos.Api.Data.Entities;

public sealed class TaskItem
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public ApplicationUser User { get; set; } = null!;
    public string Title { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? DeletedAt { get; set; }
    public DateTimeOffset? ReminderTime { get; set; }
    public string Recurrence { get; set; } = "NONE";
    public bool IsHighPriority { get; set; }
    public bool IsFullScreenReminder { get; set; }
    public List<string> Attachments { get; set; } = [];
    public bool IsCompleted { get; set; }
    public bool IsArchived { get; set; }
    public bool IsOneOffTask { get; set; }
}
