import { Component, OnDestroy, ElementRef, OnInit } from '@angular/core';

import { ProjectService } from '../../services/project/project.service';
import { Role } from '../../shared/models/role.model';
import { Subscription } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { UserProfilePopupComponent } from '../../components/user-profile-popup/user-profile-popup.component';
import { Project } from '../../shared/models/project.model';
import { NavigationService } from '../../services/navigation/navigation.service';
import { AclEntry } from '../../shared/models/acl-entry.model';
import { AuthService } from '../../services/auth/auth.service';
import { remove } from 'immutable';

@Component({
  selector: 'app-card-view-project',
  templateUrl: './card-view-project.component.html',
  styleUrls: ['./card-view-project.component.css'],
})
export class CardViewProjectComponent implements OnInit, OnDestroy {
  projects?: Project[];
  private subscription = new Subscription();
  breakpoint: number;

  constructor(
    public auth: AuthService,
    private projectService: ProjectService,
    private navigationService: NavigationService,
    private dialog: MatDialog,
    private authService: AuthService
  ) {
    this.breakpoint = window.innerWidth <= 400 ? 1 : 6;
    this.subscription.add(
      this.projectService.getAllProjects().subscribe(p => {
        p?.forEach(element => {
          const acl = this.projectService.getProjectAcl(element);
          const isValid = this.authService.canManageProject(acl);
          if (!isValid) {
            p.splice(p.indexOf(element), 1);
          }
        });
        this.projects = p;
      })
    );
  }
  ngOnInit(): void {}

  onProjectClicked(index: number) {
    this.navigationService.selectProject(this.projects![index].id);
  }

  onNewProject() {
    this.navigationService.newProject();
  }
  /**
   * Clean up Rx subscription when cleaning up the component.
   */
  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  openProfileDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog.open(UserProfilePopupComponent, {
      data: { trigger: target },
    });
  }

  onResize(event: any) {
    this.breakpoint = event.target.innerWidth <= 400 ? 1 : 6;
  }
}
