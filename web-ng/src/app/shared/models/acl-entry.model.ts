import { Role } from '../../shared/models/role.model';

export class AclEntry {
  constructor(readonly email: string, readonly role: Role) {}
}
