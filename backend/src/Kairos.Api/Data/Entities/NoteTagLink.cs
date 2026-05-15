namespace Kairos.Api.Data.Entities;

public sealed class NoteTagLink
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ClientId { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public ApplicationUser User { get; set; } = null!;
    public Guid NoteClientId { get; set; }
    public Guid TagClientId { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? DeletedAt { get; set; }
}
