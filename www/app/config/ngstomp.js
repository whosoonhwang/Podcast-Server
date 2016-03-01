import {Module, Config} from '../decorators';
import SockJS from 'sockjs-client';
import 'AngularStompDK';

@Module({
    name : 'ps.config.ngstomp',
    modules : [ 'AngularStompDK' ]
})
@Config(ngstompProvider => {"ngInject"; ngstompProvider.url('/ws').credential('login', 'password').class(SockJS);} )
export default class AngularStompDK {}