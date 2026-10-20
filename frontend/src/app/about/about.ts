import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { GITHUB_URL, HostingService } from '../core/hosting.service';

/** What Mentalist is, what it isn't, and what happens to people's data. Readable without signing in. */
@Component({
  selector: 'app-about',
  imports: [RouterLink],
  templateUrl: './about.html',
  styleUrl: './about.css',
})
export class AboutPage {
  protected readonly auth = inject(AuthService);
  protected readonly hosting = inject(HostingService);
  protected readonly githubUrl = GITHUB_URL;

  constructor() {
    this.hosting.loadConfig();
  }
}
