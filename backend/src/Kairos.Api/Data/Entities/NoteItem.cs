namespace Kairos.Api.Data.Entities;

public sealed class NoteItem
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ClientId { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public ApplicationUser User { get; set; } = null!;
    public Guid? FolderClientId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string MarkdownBody { get; set; } = string.Empty;
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? DeletedAt { get; set; }
}
