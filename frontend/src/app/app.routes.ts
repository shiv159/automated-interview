import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./components/candidate-review/candidate-review.component').then(m => m.CandidateReviewComponent)
  },
  {
    path: 'sessions/:id/analysis',
    loadComponent: () => import('./components/candidate-review/candidate-review.component').then(m => m.CandidateReviewComponent)
  },
  {
    path: 'sessions/:id/interview',
    loadComponent: () => import('./components/interview/interview.component').then(m => m.InterviewComponent)
  },
  {
    path: 'sessions/:id/report',
    loadComponent: () => import('./components/report/report.component').then(m => m.ReportComponent)
  },
  {
    path: 'question-bank',
    loadComponent: () => import('./components/question-bank/question-bank.component').then(m => m.QuestionBankComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
