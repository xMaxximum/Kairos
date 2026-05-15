namespace Kairos.Api.Data.Entities;

public sealed class NoteTaskReference
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public ApplicationUser User { get; set; } = null!;
    public Guid NoteClientId { get; set; }
    public Guid TaskClientId { get; set; }
}
