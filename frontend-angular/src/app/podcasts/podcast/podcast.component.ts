import { Component, OnInit } from '@angular/core';
import {Podcast} from '../../shared/entity';
import {Store} from '@ngrx/store';
import {ActivatedRoute} from '@angular/router';
import {toPodcast} from './core/resolver/podcast.resolver';
// import {pluck} from 'rxjs/operator/pluck';

@Component({
  selector: 'ps-podcast',
  templateUrl: './podcast.component.html',
  styleUrls: ['./podcast.component.scss']
})
export class PodcastComponent implements OnInit {

  podcast: Podcast;

  constructor(private store: Store<any>, private route: ActivatedRoute) { }

  ngOnInit() {
    this.route.data
      .map(toPodcast)
      .subscribe(v => this.podcast = v);
  }

}
