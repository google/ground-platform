import { Role } from '../../shared/models/role.model';

export class AclEntry {
  private static readonly MANAGER_ROLES = [Role.MANAGER, Role.OWNER];

  constructor(readonly email: string, readonly role: Role) {}

  public isManager(): boolean {
    return AclEntry.MANAGER_ROLES.includes(this.role);
  }
}
