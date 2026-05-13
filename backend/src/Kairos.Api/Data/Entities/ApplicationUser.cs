using Microsoft.AspNetCore.Identity;

namespace Kairos.Api.Data.Entities;

public sealed class ApplicationUser : IdentityUser<Guid>
{
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
}
