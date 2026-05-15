namespace Kairos.Api.Data.Entities;

public sealed class NoteNoteReference
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public ApplicationUser User { get; set; } = null!;
    public Guid SourceNoteClientId { get; set; }
    public Guid TargetNoteClientId { get; set; }
}
