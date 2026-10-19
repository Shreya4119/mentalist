import { Component, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GLOSSARY, GLOSSARY_GROUPS, GLOSSARY_LIST, GlossaryEntry } from './glossary';
import { LearnVisual } from './learn-visual';
import { LESSONS, Lesson, Slide, VisualKey, locateSlide } from './lessons';

const SEEN_KEY = 'mentalist_learn_seen';

/** Illustrations drawn with made-up numbers get a reminder when the slide has no caption of its own. */
const EXAMPLE_VISUALS = new Set<VisualKey>([
  'balance-blocks', 'margin-waterfall', 'roe-roa', 'liquidity', 'debt-equity', 'coverage', 'profit-vs-cash', 'fcf', 'turnover',
  'growth-bars', 'data-quality', 'outlook', 'emergency-fund', 'income-split', 'goals', 'buckets', 'concentration', 'price-vs-value',
]);

/**
 * "Learn the basics": short illustrated lessons you step through slide by slide,
 * plus the full searchable glossary ("All terms"). State lives in the URL
 * (?lesson=profit&slide=2, ?view=terms) so every slide can be linked and the
 * browser's back button works; #term links from the ⓘ tips open that term's slide.
 */
@Component({
  selector: 'app-learn',
  imports: [FormsModule, RouterLink, LearnVisual],
  templateUrl: './learn.html',
  styleUrl: './learn.css',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class LearnPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly params = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly fragment = toSignal(this.route.fragment, { initialValue: this.route.snapshot.fragment });
  private readonly heading = viewChild<ElementRef<HTMLElement>>('slideHeading');

  protected readonly glossary = GLOSSARY;
  protected readonly lessons = LESSONS;

  readonly view = computed<'lessons' | 'terms'>(() => (this.params().get('view') === 'terms' ? 'terms' : 'lessons'));

  /** The open lesson and slide, from ?lesson&slide or from a #term link. */
  readonly current = computed<{ lesson: Lesson; index: number } | null>(() => {
    if (this.view() === 'terms') return null;
    const p = this.params();
    const lesson = LESSONS.find((l) => l.id === p.get('lesson'));
    if (lesson) {
      const n = Number(p.get('slide') ?? '1');
      const index = Number.isFinite(n) ? Math.min(Math.max(Math.round(n) - 1, 0), lesson.slides.length - 1) : 0;
      return { lesson, index };
    }
    const f = this.fragment();
    return f ? locateSlide(f) : null;
  });
  readonly slide = computed<Slide | null>(() => {
    const c = this.current();
    return c ? c.lesson.slides[c.index] : null;
  });
  readonly entry = computed<GlossaryEntry | null>(() => {
    const key = this.slide()?.termKey;
    return key ? GLOSSARY[key] ?? null : null;
  });
  readonly isFirst = computed(() => this.current()?.index === 0);
  readonly isLast = computed(() => {
    const c = this.current();
    return !!c && c.index === c.lesson.slides.length - 1;
  });
  readonly nextLesson = computed(() => {
    const c = this.current();
    if (!c) return null;
    return LESSONS[LESSONS.indexOf(c.lesson) + 1] ?? null;
  });

  // ---- progress (per viewer, optional: the page works without storage)
  readonly seen = signal<Set<string>>(LearnPage.readSeen());

  private static readSeen(): Set<string> {
    try {
      return new Set(JSON.parse(localStorage.getItem(SEEN_KEY) ?? '[]') as string[]);
    } catch {
      return new Set();
    }
  }

  readonly lessonCards = computed(() => {
    const seen = this.seen();
    return LESSONS.map((l, i) => {
      const done = l.slides.filter((s) => seen.has(s.id)).length;
      const firstUnseen = l.slides.findIndex((s) => !seen.has(s.id));
      return {
        lesson: l, number: i + 1, done, total: l.slides.length,
        resume: firstUnseen < 0 ? 1 : firstUnseen + 1,
        action: done === 0 ? 'Start' : done === l.slides.length ? 'Review' : 'Continue',
        visual: l.slides.find((s) => s.visual)?.visual ?? null,
      };
    });
  });
  readonly totalSeen = computed(() => this.lessonCards().reduce((n, c) => n + c.done, 0));
  readonly totalSlides = LESSONS.reduce((n, l) => n + l.slides.length, 0);

  // ---- glossary ("All terms")
  readonly query = signal('');
  readonly groups = computed(() => {
    const q = this.query().trim().toLowerCase();
    const match = (e: GlossaryEntry) =>
      !q || [e.term, e.what, e.why, e.analogy ?? '', e.good ?? ''].some((t) => t.toLowerCase().includes(q));
    return GLOSSARY_GROUPS.map((g) => ({ name: g, entries: GLOSSARY_LIST.filter((e) => e.group === g && match(e)) }))
      .filter((g) => g.entries.length > 0);
  });
  readonly count = computed(() => this.groups().reduce((n, g) => n + g.entries.length, 0));

  private focusOnChange = false;
  private touchX: number | null = null;

  constructor() {
    effect(() => {
      const s = this.slide();
      if (!s) return;
      if (!this.seen().has(s.id)) {
        const next = new Set(this.seen()).add(s.id);
        this.seen.set(next);
        try {
          localStorage.setItem(SEEN_KEY, JSON.stringify([...next]));
        } catch {
          /* storage unavailable: progress just isn't remembered */
        }
      }
      if (this.focusOnChange) {
        this.focusOnChange = false;
        setTimeout(() => this.heading()?.nativeElement.focus({ preventScroll: true }));
      }
    });
  }

  ngOnInit(): void {
    // In "All terms", #term scrolls to and flashes that entry.
    this.route.fragment.subscribe((f) => {
      if (f && this.view() === 'terms') {
        setTimeout(() => {
          const el = document.getElementById(f);
          el?.scrollIntoView({ block: 'start' });
          el?.classList.add('flash');
          setTimeout(() => el?.classList.remove('flash'), 1600);
        }, 50);
      }
    });
  }

  /** Query params that open a given slide (used by links). */
  slideLink(id: string): Record<string, string | number> | null {
    const at = locateSlide(id);
    return at ? { lesson: at.lesson.id, slide: at.index + 1 } : null;
  }

  captionFor(s: Slide): string | null {
    return s.caption ?? (s.visual && EXAMPLE_VISUALS.has(s.visual) ? 'Example numbers, for illustration.' : null);
  }

  go(index: number): void {
    const c = this.current();
    if (!c || index < 0 || index >= c.lesson.slides.length) return;
    this.focusOnChange = true;
    this.router.navigate([], { queryParams: { lesson: c.lesson.id, slide: index + 1 }, replaceUrl: true });
  }

  next(): void {
    const c = this.current();
    if (c) this.go(c.index + 1);
  }

  prev(): void {
    const c = this.current();
    if (c) this.go(c.index - 1);
  }

  onKey(event: KeyboardEvent): void {
    if (!this.current() || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const target = event.target as HTMLElement | null;
    if (target?.closest('input, textarea, select, [contenteditable="true"], .info-pop')) return;
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.next();
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.prev();
    }
  }

  onTouchStart(event: TouchEvent): void {
    this.touchX = event.changedTouches[0]?.clientX ?? null;
  }

  onTouchEnd(event: TouchEvent): void {
    if (this.touchX === null) return;
    const dx = (event.changedTouches[0]?.clientX ?? this.touchX) - this.touchX;
    this.touchX = null;
    if (Math.abs(dx) > 60) {
      if (dx < 0) this.next();
      else this.prev();
    }
  }
}
